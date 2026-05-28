package giis.socialnetwork.e2e.functional.tests;

import giis.socialnetwork.e2e.functional.common.BaseLoggedClass;
import giis.socialnetwork.e2e.functional.common.ElementNotFoundException;
import giis.retorch.annotations.AccessMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

/**
 * Validates user registration and login flows through the Social Network web UI.
 */
class TestLogin extends BaseLoggedClass {

    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "frontend", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "web-browser", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("Submitting the signup form registers the user and redirects to the login page")
    void testRegisterViaForm() throws ElementNotFoundException {
        long ts = System.currentTimeMillis();
        navUtils.goToSignupPage(driver, waiter);

        registerViaForm("Alice", "Test", "alice" + ts, "pwd" + ts);

        // Server redirects to index.html on success — verify login form is shown
        waiter.waitForLoginPage();
        Assertions.assertTrue(
                driver.findElement(By.name("username")).isDisplayed(),
                "After registration the login page must be shown");
    }

    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "frontend", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "web-browser", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("Submitting the login form with valid credentials navigates to the main feed")
    void testLoginViaForm() throws ElementNotFoundException {
        long ts = System.currentTimeMillis();
        String username = "bob" + ts;
        String password = "pwd" + ts;

        // Register first via the signup form
        navUtils.goToSignupPage(driver, waiter);
        registerViaForm("Bob", "Test", username, password);

        // Then login via the login form
        loginViaForm(username, password);

        // Verify we are on main.html by checking the DeathStar brand in the navbar
        waiter.waitUntil(
                ExpectedConditions.textToBePresentInElementLocated(
                        By.cssSelector("a.navbar-brand"), "DeathStar"),
                "After login the main feed page with 'DeathStar' brand must be shown");
    }
}
