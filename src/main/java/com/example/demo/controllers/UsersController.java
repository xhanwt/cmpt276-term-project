package com.example.demo.controllers;



import java.util.List;
import java.util.Map;
// import java.util.Optional;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.models.User;
import com.example.demo.models.UserRepository;
// import com.example.quizapp2.models.Users;
import com.example.demo.service.TrainingPlanService;
import com.example.demo.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Controller
public class UsersController {

    @Autowired
    private UserRepository userRepo;

    private UserService userService = null;
    
    @Autowired
    public void UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    @ResponseBody
    public User createUser(@RequestBody User user) {
        return userService.saveUser(user);
    }

    //simple get request to login page
    @GetMapping("/login")
    public String loginPage() {
        return "users/loginPage";
    }
    @GetMapping("/register")
    public String registerPage() {
        return "users/registerPage";
    }

    @GetMapping("/users/logout")
    public String logout(HttpSession session) {
        System.out.println("Logging out");
        session.invalidate(); // Invalidate the session to logout the user
        return "users/loginPage";
    }

    @PostMapping("/users/login")
    public String login(@RequestParam Map<String, String> newUser,HttpSession session, HttpServletResponse response, Model model)
    {
        System.out.println("Logging in");

        String newUsername = newUser.get("username");
        String newPassword = newUser.get("password");

        // Check if username and password are present
        if (newUsername.isEmpty() || newPassword.isEmpty()) {
            response.setStatus(400); // Bad Request
            model.addAttribute("error", "Username or password not provided");
            return "users/loginPage";
        }

        User user = userRepo.findByUsername(newUsername);
        if (user == null) {
            response.setStatus(404); // Not Found
            model.addAttribute("error", "Invalid username");
            return "users/loginPage";
        }
        if (!user.getPassword().equals(newPassword)) {
            response.setStatus(401); // Unauthorized
            model.addAttribute("error", "Invalid password");
            return "users/loginPage";
        }
        //login successful
        session.setAttribute("userId", user.getUid());
        //if 0 go to user page
        if (user.getStatus() == 0) {
            response.setStatus(200); // OK
            model.addAttribute("user", user);
            return "redirect:/dashboard";
        }
        //if 1 go to coach page
        if (user.getStatus() == 1) {
            response.setStatus(200); // OK
            model.addAttribute("user", user);
            return "redirect:/dashboard"; //coach view is same as user for now, more to come
        }

        //default to login page
        response.setStatus(401); // Unauthorized
        model.addAttribute("error", "Invalid status");
        return "users/loginPage";
    }

    @PostMapping("/users/register")
    public String registerUser(@RequestParam Map<String, String> newUser, HttpServletResponse response, Model model)
    {
        System.out.println("ADD user");

        String newUsername = newUser.get("username");
        int newStatus = Integer.parseInt(newUser.getOrDefault("status", "0"));
        String newPassword = newUser.get("password");


        // Check if username and password are present
        if (newUsername.isEmpty() || newPassword.isEmpty()) {
            response.setStatus(400); // Bad Request
            model.addAttribute("error", "Username or password not provided");
            return "users/registerPage";
        }

        //username already exists
        if (userRepo.existsByUsername(newUsername)) {
            System.out.println("Username already exists");
            response.setStatus(409); // Conflict
            model.addAttribute("error", "Username already exists");
            return "users/registerPage";
        }
      
        userRepo.save(new User(newUsername, newPassword, newStatus));
        response.setStatus(201);
        model.addAttribute("success", "User created");
        return "users/loginPage";
    }

    
    @PostMapping("/users/deleteAll")
    public String deleteAllUsers(HttpServletResponse response)
    {
    System.out.println("DELETE all users");
    userRepo.deleteAll();
    response.setStatus(204); 
    return "redirect:/users/view";
    }







    @Autowired
    private TrainingPlanService trainingPlanService;

    @GetMapping("/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        Integer uId = (Integer) session.getAttribute("userId");
        if (uId == null) {
            return "redirect:/login"; 
        }

        Optional<User> uOptional = userRepo.findById(uId);
        if (!uOptional.isPresent()) {
            session.invalidate();
            return "redirect:/login";
        }
        User loggedInUser = uOptional.get();

        Boolean planExists = trainingPlanService.checkForUserTrainingPlan(loggedInUser.getUid());
        System.out.println("Plan exists: " + planExists); // Debugging line
        boolean hasTrainingPlan = Optional.ofNullable(planExists).orElse(false);

        model.addAttribute("hasTrainingPlan", hasTrainingPlan);
        model.addAttribute("user", loggedInUser);
        return "users/dashboard"; 
    }

    
    @GetMapping("/accEdit/{userId}")
    public String editAccountForm(@PathVariable("userId") Integer uId, HttpSession session, Model model, HttpServletRequest request) {
        Integer sessionUid = (Integer) session.getAttribute("userId");
        if (sessionUid == null || !sessionUid.equals(uId)) {
            return "redirect:/login";
        }

        Optional<User> uOptional = userRepo.findById(uId);
        if (!uOptional.isPresent()) {
            return "redirect:/login"; 
        }

        model.addAttribute("user", uOptional.get());
        return "users/accEdit"; 
    }

    @PostMapping("/accEdit/{userId}")
    public String updateAccount(@PathVariable("userId") Integer uId, @RequestParam Map<String, String> params, HttpSession session, RedirectAttributes redirectAttributes) {
        Integer sessionUid = (Integer) session.getAttribute("userId");
        if (sessionUid == null || !sessionUid.equals(uId)) {
            return "redirect:/login";
        }

        User user = userRepo.findById(uId).orElse(null);
        if (user == null) {
            return "redirect:/login"; 
        }
        String newUname = params.get("username");
        if (!user.getUsername().equals(newUname) && userRepo.existsByUsername(newUname)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Username already exists. Please choose a different username.");
            return "redirect:/accEdit/" + uId; 
        }

        user.setUsername(params.get("username"));
        user.setWeight(Double.valueOf(params.get("weight")));
        user.setHeight(Double.valueOf(params.get("height")));
        userService.saveUser(user);
        return "redirect:/dashboard"; 
    }  


}

