package giis.socialnetwork.e2e.functional.pages;

import giis.socialnetwork.e2e.functional.common.ElementNotFoundException;
import giis.socialnetwork.e2e.functional.utils.Click;
import giis.socialnetwork.e2e.functional.utils.Waiter;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class LoginPage {

    private final WebDriver driver;
    private final Waiter waiter;
    private final String sutUrl;

    private static final By USERNAME   = By.name("username");
    private static final By PASSWORD   = By.name("password");
    private static final By LOGIN_BTN  = By.cssSelector("input[name='login']");
    private static final By SIGN_UP    = By.linkText("Sign Up");

    public LoginPage(WebDriver driver, Waiter waiter, String sutUrl) {
        this.driver = driver;
        this.waiter = waiter;
        this.sutUrl = sutUrl;
    }

    public LoginPage open() {
        driver.get(sutUrl + "/index.html");
        waiter.waitForLoginPage();
        return this;
    }

    public MainPage login(String username, String password) throws ElementNotFoundException {
        waiter.waitForLoginPage();
        fill(USERNAME, username);
        fill(PASSWORD, password);
        waiter.waitUntil(ExpectedConditions.elementToBeClickable(LOGIN_BTN), "Login button not clickable");
        Click.element(driver, driver.findElement(LOGIN_BTN));
        waiter.waitForMainPage();
        return new MainPage(driver, waiter, sutUrl);
    }

    public boolean isUsernameDisplayed()  { return driver.findElement(USERNAME).isDisplayed(); }
    public boolean isPasswordDisplayed()  { return driver.findElement(PASSWORD).isDisplayed(); }
    public boolean isSignUpLinkDisplayed() { return driver.findElement(SIGN_UP).isDisplayed(); }

    private void fill(By locator, String value) {
        WebElement f = driver.findElement(locator);
        f.clear();
        f.sendKeys(value);
    }
}
