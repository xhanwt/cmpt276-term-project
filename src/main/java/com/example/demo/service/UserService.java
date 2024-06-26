package com.example.demo.service;

import com.example.demo.models.User;
import com.example.demo.models.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserService {
    private final int USERNAME_MIN_LENGTH = 4;
    private final int PASSWORD_MIN_LENGTH = 8;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public boolean isValidPassword(String password) {
        boolean hasUppercase = !password.equals(password.toLowerCase());
        boolean hasLowercase = !password.equals(password.toUpperCase());

        boolean hasSymbol = password.matches(".*\\W.*");
        boolean isLongEnough = password.length() >= PASSWORD_MIN_LENGTH;

        return hasUppercase && hasLowercase && hasSymbol && isLongEnough;
    }

    public Validation validatePassword(String newPassword) {
        Validation toRet = new Validation();
        if (newPassword.isEmpty()) {
            return new Validation(400, "Password must be provided");
        } else if (newPassword.length() < PASSWORD_MIN_LENGTH) {
            return new Validation(400, "Password must be at least " + PASSWORD_MIN_LENGTH + " characters");
        } else if (!isValidPassword(newPassword)) {
            return new Validation(400,
                    "Password must include at least one uppercase letter, one lowercase letter, and one symbol");
        }
        return toRet;
    }

    public Validation validateUsername(String newUsername) {
        if (newUsername.isEmpty()) {
            return new Validation(400, "Username must be provided");
        } else if (newUsername.length() < USERNAME_MIN_LENGTH) {
            return new Validation(400, "Username must be at least " + USERNAME_MIN_LENGTH + " characters");
        } else if (userRepository.existsByUsername(newUsername)) {
            return new Validation(409, "Username already exists");
        }

        // default constructor creates a non-error (valid)
        return new Validation();
    }

    // login version, bypass the error of username already exists
    public Validation validateUsernameLogin(String newUsername) {
        Validation error = validateUsername(newUsername);
        if (error.message.equals("Username already exists")) {
            return new Validation();
        } else {
            return error;
        }
    }

    public Validation validateLogin(String newUsername, String newPassword) {
        Validation[] validations = { validateUsernameLogin(newUsername), validatePassword(newPassword) };
        for (Validation validation : validations) {
            if (validation.isError) {
                return validation;
            }
        }

        User user = userRepository.findByUsername(newUsername);
        boolean userNotFound = (user == null);
        boolean incorrectPassword = true;
        // System.out.print("INCOMING PASSWORD LOGIN: " + newPassword);
        RestTemplate restTemplate = new RestTemplate();
        String hashifyUrl = "https://api.hashify.net/hash/md5/hex?value=" + newPassword;
        String hashifyResponse = restTemplate.getForObject(hashifyUrl, String.class);
        newPassword = extractMD5Hash(hashifyResponse);
        // System.out.println("CONVERTED PASSSWROD TO: " + newPassword);
        if (user != null) {
            incorrectPassword = !user.getPassword().equals(newPassword);
        }
        if (userNotFound || incorrectPassword) {
            return new Validation(401, "Incorrect username or password.");
        }
        return new Validation();
    }

    public Validation validateRegistration(String newUsername, String newPassword) {
        Validation[] validations = { validateUsername(newUsername), validatePassword(newPassword) };
        for (Validation validation : validations) {
            if (validation.isError) {
                return validation;
            }
        }
        return new Validation();
    }

    private String extractMD5Hash(String responseJson) {
        int startIndex = responseJson.indexOf(":") + 2;
        int endIndex = responseJson.indexOf("\",");
        return responseJson.substring(startIndex, endIndex);
    }
}
