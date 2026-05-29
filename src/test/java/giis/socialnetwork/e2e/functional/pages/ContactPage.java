package giis.socialnetwork.e2e.functional.pages;

import giis.socialnetwork.e2e.functional.utils.Waiter;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class ContactPage {

    private final WebDriver driver;
    private final Waiter waiter;
    private final String sutUrl;

    private static final By FOLLOWER_LIST = By.id("follower-list");
    private static final By FOLLOWEE_LIST = By.id("followee-list");

    public ContactPage(WebDriver driver, Waiter waiter, String sutUrl) {
        this.driver = driver;
        this.waiter = waiter;
        this.sutUrl = sutUrl;
    }

    public ContactPage open() {
        driver.get(sutUrl + "/contact.html");
        waiter.waitForContactPage();
        return this;
    }

    public boolean isFollowerListDisplayed() { return !driver.findElements(FOLLOWER_LIST).isEmpty(); }
    public boolean isFolloweeListDisplayed() { return !driver.findElements(FOLLOWEE_LIST).isEmpty(); }
}
