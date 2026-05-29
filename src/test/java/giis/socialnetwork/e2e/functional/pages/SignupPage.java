package giis.socialnetwork.e2e.functional.pages;

import giis.socialnetwork.e2e.functional.common.ElementNotFoundException;
import giis.socialnetwork.e2e.functional.utils.Click;
import giis.socialnetwork.e2e.functional.utils.Waiter;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class SignupPage {

    private final WebDriver driver;
    private final Waiter waiter;
    private final String sutUrl;

    private static final By FIRST_NAME  = By.name("first_name");
    private static final By LAST_NAME   = By.name("last_name");
    private static final By USERNAME    = By.name("username");
    private static final By PASSWORD    = By.name("password");
    private static final By SIGNUP_BTN  = By.cssSelector("input[name='signup']");
    private static final By LOGIN_LINK  = By.linkText("Login");

    public SignupPage(WebDriver driver, Waiter waiter, String sutUrl) {
        this.driver = driver;
        this.waiter = waiter;
        this.sutUrl = sutUrl;
    }

    public SignupPage open() {
        driver.get(sutUrl + "/signup.html");
        waiter.waitForSignupPage();
        return this;
    }

    public LoginPage register(String firstName, String lastName, String username, String password)
            throws ElementNotFoundException {
        waiter.waitForSignupPage();
        fill(FIRST_NAME, firstName);
        fill(LAST_NAME, lastName);
        fill(USERNAME, username);
        fill(PASSWORD, password);
        waiter.waitUntil(ExpectedConditions.elementToBeClickable(SIGNUP_BTN), "Signup button not clickable");
        Click.element(driver, driver.findElement(SIGNUP_BTN));
        waiter.waitForLoginPage();
        return new LoginPage(driver, waiter, sutUrl);
    }

    public boolean isFirstNameDisplayed()  { return driver.findElement(FIRST_NAME).isDisplayed(); }
    public boolean isLastNameDisplayed()   { return driver.findElement(LAST_NAME).isDisplayed(); }
    public boolean isUsernameDisplayed()   { return driver.findElement(USERNAME).isDisplayed(); }
    public boolean isPasswordDisplayed()   { return driver.findElement(PASSWORD).isDisplayed(); }
    public boolean isLoginLinkDisplayed()  { return driver.findElement(LOGIN_LINK).isDisplayed(); }

    private void fill(By locator, String value) {
        WebElement f = driver.findElement(locator);
        f.clear();
        f.sendKeys(value);
    }
}
