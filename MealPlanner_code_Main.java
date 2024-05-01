package mealplanner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;

public class Main {

    //String regex to make sure that ingredients are entered comma seperated without special chars
    private static final String regexIngredients = "^(?!\\s*$)([\\p{L}\\s]+)+$";

    //A hashmap to keep our dayPlans for checking if the plan has been created before
    private static Map<String, mealplanner.Main.DayOfWeek> dayPlans = new HashMap<>();

    public static void main(String[] args) throws SQLException {
        //--------POSTGRES BACKEND----------
        String DB_URL = "jdbc:postgresql:meals_db";
        String USER = ""; //removed to preserve username
        String PASS = ""; //removed to preserve password

        Connection connection = DriverManager.getConnection(DB_URL, USER, PASS);
        connection.setAutoCommit(true);

        Statement statement = connection.createStatement();
        //create meals and increment for PK
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS meals (" +
                "meal_id INTEGER PRIMARY KEY," +
                "category VARCHAR(255)," +
                "meal VARCHAR(255)" +
                ")");
        statement.executeUpdate("CREATE SEQUENCE IF NOT EXISTS meal_id_seq");


        //create ingrediesnts and increment for PK
        statement.executeUpdate("CREATE TABLE IF NOT EXISTS ingredients (" +
                "ingredient_id INTEGER," +
                "ingredient VARCHAR(255)," +
                "meal_id INTEGER," +
                "FOREIGN KEY (meal_id) REFERENCES meals(meal_id)" +
                ")");
        statement.executeUpdate("CREATE SEQUENCE IF NOT EXISTS ingredient_id_seq");

        //create our meal_plans to save them into the database
        //statement.executeUpdate("DROP TABLE meal_plans");

        statement.executeUpdate("CREATE TABLE IF NOT EXISTS meal_plans (" + //
                "day VARCHAR(255)," +
                "breakfast VARCHAR(255)," +
                "lunch VARCHAR(255)," +
                "dinner VARCHAR(255)" +
                ")");
        statement.close();



        //---------Code for console insertion-----------
        Scanner scanner = new Scanner(System.in);

        // Populate dayPlans map from the database
        populateDayPlansFromDatabase(connection);

        //main menu to get user input and make sure that inputs are match to lowercase
        //prints feedback if the user fails to input data properly
        while (true) {
            System.out.println("What would you like to do (add, show, plan, save, exit)?");
            String input = scanner.nextLine();
            if (input.toLowerCase().trim().equals("add")) {
                addMeal(scanner, connection);
            } else if (input.toLowerCase().trim().equals("show")) {
                System.out.println("Which category do you want to print (breakfast, lunch, dinner)?");
                String category = "";
                while (true) {
                    category = scanner.nextLine().trim();
                    if (!category.toLowerCase().trim().equals("breakfast") &&
                            !category.toLowerCase().trim().equals("lunch") &&
                            !category.toLowerCase().trim().equals("dinner")) {
                        System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
                    } else {
                        break;
                    }
                }
                showMeals(connection, category);
            } else if (input.toLowerCase().trim().equals("plan")) {
                planMeals(scanner, connection);
            } else if(input.toLowerCase().trim().equals("save")) {
                savePlan(connection, scanner, dayPlans);
            } else if (input.toLowerCase().trim().equals("exit")) {
                System.out.println("Bye!");
                break;
            }
        }

        connection.close();
    }

    private static void addMeal(Scanner scanner, Connection connection) throws SQLException {
        String category = "";
        System.out.println("Which meal do you want to add (breakfast, lunch, dinner)?");
        while (true) {
            category = scanner.nextLine().trim();
            if (!category.toLowerCase().trim().equals("breakfast") &&
                    !category.toLowerCase().trim().equals("lunch") &&
                    !category.toLowerCase().trim().equals("dinner")) {
                System.out.println("Wrong meal category! Choose from: breakfast, lunch, dinner.");
            } else {
                break;
            }
        }

        //makes sure the meals name does not have any special chars numbers or spaces
        System.out.println("Input the meal's name:");
        String name = "";
        while (true) {
            name = scanner.nextLine().trim();
            if (!name.matches("^(?!\\s*$)([\\p{L}\\s]+)+$")) {
                System.out.println("Wrong format. Use letters only!");
            } else {
                break;
            }
        }


        //makes sure the ingredients does not have any special chars numbers or spaces
        //also checks seperation like "lettuce,tomato,ranch"
        System.out.println("Input the ingredients:");
        String mealIngredients = scanner.nextLine().trim();
        String[] ingredients = mealIngredients.split(",");

        // Insert meal into meals table
        int mealId = generateId(connection, "meals", "meal_id");
        PreparedStatement mealStatement = connection.prepareStatement(
                "INSERT INTO meals (meal_id, category, meal) VALUES (?, ?, ?)"

        );
        mealStatement.setInt(1, mealId);
        mealStatement.setString(2, category);
        mealStatement.setString(3, name);
        mealStatement.executeUpdate();

        mealStatement.close();

        // Insert ingredients into ingredients table
        for (String ingredient : ingredients) {
            int ingredientId = generateId(connection, "ingredients", "ingredient_id");
            PreparedStatement ingredientStatement = connection.prepareStatement(
                    "INSERT INTO ingredients (ingredient_id, ingredient, meal_id) VALUES (?, ?, ?)"
            );
            ingredientStatement.setInt(1, ingredientId);
            ingredientStatement.setString(2, ingredient.trim());
            ingredientStatement.setInt(3, mealId);
            ingredientStatement.executeUpdate();
            ingredientStatement.close();
        }

        System.out.println("The meal has been added!");
    }


    private static List<mealplanner.Main.Meal> showMeals(Connection connection, String category) throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT * FROM meals WHERE category = '" + category + "'");

        List<mealplanner.Main.Meal> meals = new ArrayList<>();

        while (rs.next()) {
            String mealName = rs.getString("meal");
            List<String> ingredients = new ArrayList<>();
            PreparedStatement ingredientStatement = connection.prepareStatement(
                    "SELECT * FROM ingredients WHERE meal_id = ?"
            );
            ingredientStatement.setInt(1, rs.getInt("meal_id"));
            ResultSet ingredientResultSet = ingredientStatement.executeQuery();
            while (ingredientResultSet.next()) {
                ingredients.add(ingredientResultSet.getString("ingredient"));
            }
            meals.add(new mealplanner.Main.Meal(category, mealName, ingredients.toArray(new String[0])));
            ingredientStatement.close();
        }

        if (meals.isEmpty()) {
            System.out.println("No meals found.");
        } else {
            System.out.println("Category: " + category);
            for (mealplanner.Main.Meal meal : meals) {
                System.out.println("Name: " + meal.name); // Accessing directly since there are no getters
                System.out.println("Ingredients:");
                for (String ingredient : meal.ingredients) { // Accessing directly since there are no getters
                    System.out.println(ingredient);
                }
                System.out.println();
            }
        }

        statement.close();
        return meals;
    }

    private static List<mealplanner.Main.Meal> getMeals(Connection connection, String category) throws SQLException{
        List<mealplanner.Main.Meal> meals = new ArrayList<>();
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT * FROM meals WHERE category = '" + category + "'");

        while(rs.next()){
            String mealName = rs.getString("meal");
            List<String> ingredients = new ArrayList<>();
            PreparedStatement ingredientStatement = connection.prepareStatement(
                    "SELECT * FROM ingredients WHERE meal_id = ?"
            );
            ingredientStatement.setInt(1, rs.getInt("meal_id"));
            ResultSet ingredientResultSet = ingredientStatement.executeQuery();
            while (ingredientResultSet.next()){
                ingredients.add(ingredientResultSet.getString("ingredient"));
            }
            meals.add(new mealplanner.Main.Meal(category, mealName, ingredients.toArray(new String[0])));
            ingredientStatement.close();
        }

        statement.close();
        meals.sort(Comparator.comparing(meal -> meal.name));
        return meals;
    }

    private static void planMeals(Scanner scanner, Connection connection) throws SQLException {
        String day = "Monday";
        mealplanner.Main.DayOfWeek monday = dayPlan(scanner, connection, day);
        //populate our dayPlans hashmap
        saveDayPlan(connection, "Monday", monday);

        day = "Tuesday";
        mealplanner.Main.DayOfWeek tuesday = dayPlan(scanner, connection, day);
        saveDayPlan(connection, "Tuesday", tuesday);

        day = "Wednesday";
        mealplanner.Main.DayOfWeek wednesday = dayPlan(scanner, connection, day);
        saveDayPlan(connection, "Wednesday", wednesday);

        day = "Thursday";
        mealplanner.Main.DayOfWeek thursday = dayPlan(scanner, connection, day);
        saveDayPlan(connection, "Thursday", thursday);

        day = "Friday";
        mealplanner.Main.DayOfWeek friday = dayPlan(scanner, connection, day);
        saveDayPlan(connection, "Friday", friday);

        day = "Saturday";
        mealplanner.Main.DayOfWeek saturday = dayPlan(scanner, connection, day);
        saveDayPlan(connection, "Saturday", saturday);

        day = "Sunday";
        mealplanner.Main.DayOfWeek sunday = dayPlan(scanner, connection, day);
        saveDayPlan(connection, "Sunday", sunday);

        //Print our schedule
        System.out.println(monday);
        System.out.println(tuesday);
        System.out.println(wednesday);
        System.out.println(thursday);
        System.out.println(friday);
        System.out.println(saturday);
        System.out.println(sunday);

    }

    private static void populateDayPlansFromDatabase(Connection connection) throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT * FROM meal_plans");

        while (resultSet.next()) {
            String day = resultSet.getString("day");
            String breakfast = resultSet.getString("breakfast");
            String lunch = resultSet.getString("lunch");
            String dinner = resultSet.getString("dinner");
            dayPlans.put(day, new mealplanner.Main.DayOfWeek(day, breakfast, lunch, dinner));
        }

        statement.close();
    }

    private static void saveDayPlan(Connection connection, String day, mealplanner.Main.DayOfWeek dayPlan) throws SQLException {
        // Insert or update the meal plan for the specified day in the database
        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO meal_plans (day, breakfast, lunch, dinner) VALUES (?, ?, ?, ?) "
        );
        statement.setString(1, day);
        statement.setString(2, dayPlan.getBreakfast());
        statement.setString(3, dayPlan.getLunch());
        statement.setString(4, dayPlan.getDinner());
        statement.executeUpdate();
        statement.close();
    }

    private static mealplanner.Main.DayOfWeek dayPlan(Scanner scanner, Connection connection, String day) throws SQLException {
        System.out.println(day);

        // Retrieve breakfast meals
        List<mealplanner.Main.Meal> breakfastMeals = getMeals(connection, "breakfast");
        for (mealplanner.Main.Meal meal : breakfastMeals){
            System.out.println(meal.name);
        }
        System.out.println("Choose the breakfast for " + day + " from the list above:");
        String breakfast = chooseMeal(scanner, breakfastMeals);

        // Retrieve lunch meals
        List<mealplanner.Main.Meal> lunchMeals = getMeals(connection, "lunch");
        for (mealplanner.Main.Meal meal : lunchMeals){
            System.out.println(meal.name);
        }
        System.out.println("Choose the lunch for " + day + " from the list above:");
        String lunch = chooseMeal(scanner, lunchMeals);

        // Retrieve dinner meals
        List<mealplanner.Main.Meal> dinnerMeals = getMeals(connection, "dinner");
        for (mealplanner.Main.Meal meal : dinnerMeals){
            System.out.println(meal.name);
        }
        System.out.println("Choose the dinner for " + day + " from the list above:");
        String dinner = chooseMeal(scanner, dinnerMeals);


        System.out.println("Yeah! We planned the meals for " + day + ".");
        return new mealplanner.Main.DayOfWeek(day, breakfast, lunch, dinner);
    }

    private static String chooseMeal(Scanner scanner, List<mealplanner.Main.Meal> meals) {
        while (true) {
            String input = scanner.nextLine().trim();
            boolean mealExists = meals.stream().anyMatch(meal -> meal.name.equalsIgnoreCase(input));
            if (mealExists) {
                return input;
            } else {
                System.out.println("This meal doesn’t exist. Choose a meal from the list above.");
            }
        }
    }

    private static int generateId(Connection connection, String table, String idColumn) throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT MAX(" + idColumn + ") FROM " + table);
        int maxId = 0;
        if (rs.next()) {
            maxId = rs.getInt(1);
        }
        statement.close();
        return maxId + 1;
    }

    private static void collectIngredients(Connection connection, String mealName, Map<String, Integer> ingredientsMap) throws SQLException {
        String query = "SELECT ingredients.ingredient FROM meals JOIN ingredients ON meals.meal_id = ingredients.meal_id WHERE meals.meal = ?";
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, mealName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String ingredient = resultSet.getString("ingredient");
                    ingredientsMap.put(ingredient, ingredientsMap.getOrDefault(ingredient, 0) + 1);
                }
            }
        }
    }

    private static void savePlan(Connection connection, Scanner scanner, Map<String, mealplanner.Main.DayOfWeek> dayPlans) throws SQLException{

        if (dayPlans.isEmpty()) {
            System.out.println("Unable to save. Plan your meals first.");
            return;
        }


        System.out.println("Input a filename:");
        String filename = scanner.nextLine();

        Map<String, Integer> ingredientsMap = new HashMap<>();

        // Iterate over each planned day to collect ingredients
        for (mealplanner.Main.DayOfWeek day : dayPlans.values()) {
            // Get breakfast ingredients
            collectIngredients(connection, day.getBreakfast(), ingredientsMap);
            // Get lunch ingredients
            collectIngredients(connection, day.getLunch(), ingredientsMap);
            // Get dinner ingredients
            collectIngredients(connection, day.getDinner(), ingredientsMap);
        }

        try (PrintWriter writer = new PrintWriter(filename)) {
            for (Map.Entry<String, Integer> entry : ingredientsMap.entrySet()) {
                writer.println(entry.getValue() > 1 ? entry.getKey() + " x" + entry.getValue() : entry.getKey());
            }
            System.out.println("Saved!");
        } catch (FileNotFoundException e) {
            System.out.println("Error: Unable to save the file.");
        }
    }

    public static boolean verifyIngredients(String[] ingredients) {
        boolean isValid = true;
        for (String ingredient : ingredients) {
            if (!ingredient.matches(regexIngredients)) {
                isValid = false;
                break;
            }
        }
        return isValid;
    }

    static class DayOfWeek {

        private String name;
        private String breakfast;
        private String lunch;
        private String dinner;

        public DayOfWeek(String name, String breakfast, String lunch, String dinner){
            this.name = name;
            this.breakfast = breakfast;
            this.lunch = lunch;
            this.dinner = dinner;
        }

        public String getName(){
            return name;
        }

        public String getBreakfast(){
            return breakfast;
        }

        public String getLunch(){
            return lunch;
        }

        public String getDinner(){
            return dinner;
        }

        @Override
        public String toString(){
            StringBuilder plan_string = new StringBuilder();
            plan_string.append(name).append("\n");
            plan_string.append("Breakfast: ").append(breakfast).append("\n");
            plan_string.append("Lunch: ").append(lunch).append("\n");
            plan_string.append("Dinner: ").append(dinner).append("\n");

            return plan_string.toString();
        }
    }

    static class Meal {
        private String category;
        private String name;
        private String[] ingredients;

        public Meal(String category, String name, String[] ingredients) {
            this.category = category;
            this.name = name;
            this.ingredients = ingredients;
        }

        @Override
        public String toString() {
            StringBuilder meal_string = new StringBuilder();
            meal_string.append("\n");
            meal_string.append("Category: ").append(category).append("\n");
            meal_string.append("Name: ").append(name).append("\n");
            meal_string.append("Ingredients: ");
            if (ingredients.length > 0) {
                for(String ingredient : ingredients){
                    meal_string.append(ingredient).append("\n");
                }
            } else {
                meal_string.append("No ingredients listed");
            }

            return meal_string.toString();
        }
    }
}