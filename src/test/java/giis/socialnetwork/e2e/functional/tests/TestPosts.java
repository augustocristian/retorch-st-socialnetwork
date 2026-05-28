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

/**
 * Validates post-composition flows through the Social Network web UI.
 */
class TestPosts extends BaseLoggedClass {

    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "post", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "frontend", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "web-browser", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("Post compose form is visible on the main feed page after login")
    void testComposeFormVisible() throws ElementNotFoundException {
        long ts = System.currentTimeMillis();
        String username = "poster" + ts;

        navUtils.goToSignupPage(driver, waiter);
        registerViaForm("Post", "Er", username, "pwd" + ts);
        loginViaForm(username, "pwd" + ts);

        // The compose area is on main.html
        waiter.waitUntil(
                ExpectedConditions.visibilityOfElementLocated(By.id("post-content")),
                "Post textarea must be visible on the main page");
        waiter.waitUntil(
                ExpectedConditions.visibilityOfElementLocated(By.id("create-post")),
                "Create post button must be visible on the main page");

        WebElement textarea = driver.findElement(By.id("post-content"));
        Assertions.assertTrue(textarea.isDisplayed(), "Post compose textarea must be displayed");
    }

    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "post", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "user-timeline", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "frontend", concurrency = 10, sharing = true, accessMode = "READONLY")
    @AccessMode(resID = "web-browser", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("Composing a post via the form makes it appear in the user's own timeline")
    void testComposePostAppearsInTimeline() throws ElementNotFoundException {
        long ts = System.currentTimeMillis();
        String username = "timelineui" + ts;
        String postText = "UI test post " + ts;

        navUtils.goToSignupPage(driver, waiter);
        registerViaForm("Timeline", "Ui", username, "pwd" + ts);
        loginViaForm(username, "pwd" + ts);

        // Fill the post textarea and click Create
        waiter.waitUntil(
                ExpectedConditions.visibilityOfElementLocated(By.id("post-content")),
                "Post textarea must be ready");
        driver.findElement(By.id("post-content")).sendKeys(postText);
        driver.findElement(By.id("create-post")).click();

        // Navigate to main.html and wait for the post to appear in the timeline with retries
        navUtils.goToMainPage(driver, waiter);
        waiter.waitForPostText(postText, driver);

        Assertions.assertTrue(driver.getPageSource().contains(postText),
                "Composed post '" + postText + "' must appear in the timeline after creation");
    }
}
