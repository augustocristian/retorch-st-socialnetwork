package giis.socialnetwork.e2e.functional.utils;

import org.openqa.selenium.WebDriver;

public class Navigation {

    private final String sutUrl;

    public Navigation(String sutUrl) {
        this.sutUrl = sutUrl;
    }

    /** Navigate directly to the login page (index.html). */
    public void goToLoginPage(WebDriver driver, Waiter waiter) {
        driver.get(sutUrl + "/index.html");
        waiter.waitForLoginPage();
    }

    /** Navigate directly to the signup page. */
    public void goToSignupPage(WebDriver driver, Waiter waiter) {
        driver.get(sutUrl + "/signup.html");
        waiter.waitForSignupPage();
    }

    /** Navigate directly to the main feed page (requires active session cookie). */
    public void goToMainPage(WebDriver driver, Waiter waiter) {
        driver.get(sutUrl + "/main.html");
        waiter.waitForMainPage();
    }

    /** Navigate directly to the contacts / social-graph page. */
    public void goToContactPage(WebDriver driver, Waiter waiter) {
        driver.get(sutUrl + "/contact.html");
        waiter.waitForContactPage();
    }
}
