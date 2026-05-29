package giis.socialnetwork.e2e.functional.pages;

import giis.socialnetwork.e2e.functional.utils.Waiter;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

public class MainPage {

    private final WebDriver driver;
    private final Waiter waiter;
    private final String sutUrl;

    private static final By NAVBAR_BRAND = By.cssSelector("a.navbar-brand");
    private static final By NAV_LINKS    = By.cssSelector(".navbar-nav .nav-link");
    private static final By SHOW_POST    = By.id("show-post");
    private static final By POST_CONTENT = By.id("post-content");
    private static final By CREATE_POST  = By.id("create-post");

    public MainPage(WebDriver driver, Waiter waiter, String sutUrl) {
        this.driver = driver;
        this.waiter = waiter;
        this.sutUrl = sutUrl;
    }

    public MainPage open() {
        driver.get(sutUrl + "/main.html");
        waiter.waitForMainPage();
        return this;
    }

    public MainPage composePost(String text) {
        waiter.waitUntil(ExpectedConditions.visibilityOfElementLocated(POST_CONTENT), "Post textarea not visible");
        driver.findElement(POST_CONTENT).sendKeys(text);
        driver.findElement(CREATE_POST).click();
        return this;
    }

    public void waitForPost(String text) {
        waiter.waitForPostText(text, driver);
    }

    public ContactPage goToContacts() {
        driver.get(sutUrl + "/contact.html");
        waiter.waitForContactPage();
        return new ContactPage(driver, waiter, sutUrl);
    }

    public boolean hasNavLink(String text) {
        return driver.findElements(NAV_LINKS).stream().anyMatch(e -> e.getText().contains(text));
    }

    public boolean isComposeFormVisible() {
        return driver.findElement(POST_CONTENT).isDisplayed()
                && driver.findElement(CREATE_POST).isDisplayed();
    }

    public String getBrandText() {
        return driver.findElement(NAVBAR_BRAND).getText();
    }

    public boolean hasPostText(String text) {
        return driver.getPageSource().contains(text);
    }
}
