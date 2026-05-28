package giis.socialnetwork.e2e.functional.tests;

import giis.socialnetwork.e2e.functional.common.BaseLoggedClass;
import giis.socialnetwork.e2e.functional.common.ElementNotFoundException;
import giis.retorch.annotations.AccessMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

/**
 * Verifies that the main pages of the Social Network frontend load correctly
 * and that the navigation structure is present.
 */
class TestNavigation extends BaseLoggedClass {

    @AccessMode(resID = "frontend", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "web-browser", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("Login page loads and displays the login form with a Sign Up link")
    void testLoginPageStructure() {
        waiter.waitForLoginPage();
        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        driver.findElement(By.name("username")).isDisplayed(),
                        "Username field must be visible on the login page"),
                () -> Assertions.assertTrue(
                        driver.findElement(By.name("password")).isDisplayed(),
                        "Password field must be visible on the login page"),
                () -> Assertions.assertTrue(
                        driver.findElement(By.linkText("Sign Up")).isDisplayed(),
                        "Sign Up link must be visible on the login page")
        );
    }

    @AccessMode(resID = "frontend", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "web-browser", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("Signup page loads and displays the registration form with a Login link")
    void testSignupPageStructure() {
        navUtils.goToSignupPage(driver, waiter);
        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        driver.findElement(By.name("first_name")).isDisplayed(),
                        "first_name field must be visible on the signup page"),
                () -> Assertions.assertTrue(
                        driver.findElement(By.name("last_name")).isDisplayed(),
                        "last_name field must be visible on the signup page"),
                () -> Assertions.assertTrue(
                        driver.findElement(By.name("username")).isDisplayed(),
                        "username field must be visible on the signup page"),
                () -> Assertions.assertTrue(
                        driver.findElement(By.name("password")).isDisplayed(),
                        "password field must be visible on the signup page"),
                () -> Assertions.assertTrue(
                        driver.findElement(By.linkText("Login")).isDisplayed(),
                        "Login link must be visible on the signup page")
        );
    }

    @AccessMode(resID = "frontend", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "web-browser", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("Main page navbar contains Post and Contacts links after login")
    void testMainPageNavbar() throws ElementNotFoundException {
        long ts = System.currentTimeMillis();
        String username = "nav" + ts;
        registerViaForm("Nav", "User", username, "pwd" + ts);
        loginViaForm(username, "pwd" + ts);

        waiter.waitUntil(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".navbar-nav")),
                "Navbar did not load on main page");

        List<WebElement> navLinks = driver.findElements(By.cssSelector(".navbar-nav .nav-link"));
        boolean hasPost = navLinks.stream().anyMatch(e -> e.getText().contains("Post"));
        boolean hasContacts = navLinks.stream().anyMatch(e -> e.getText().contains("Contacts"));

        Assertions.assertAll(
                () -> Assertions.assertTrue(hasPost, "Main page navbar must contain a 'Post' link"),
                () -> Assertions.assertTrue(hasContacts, "Main page navbar must contain a 'Contacts' link")
        );
    }

    @AccessMode(resID = "frontend", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "web-browser", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("Contact page loads with Follower List and Followee List sections after login")
    void testContactPageSections() throws ElementNotFoundException {
        long ts = System.currentTimeMillis();
        String username = "cnt" + ts;
        registerViaForm("Cnt", "User", username, "pwd" + ts);
        loginViaForm(username, "pwd" + ts);

        navUtils.goToContactPage(driver, waiter);

        Assertions.assertAll(
                () -> Assertions.assertTrue(
                        driver.findElement(By.id("follower-list")).isDisplayed(),
                        "follower-list section must be visible on the contact page"),
                () -> Assertions.assertTrue(
                        driver.findElement(By.id("followee-list")).isDisplayed(),
                        "followee-list section must be visible on the contact page")
        );
    }
}
