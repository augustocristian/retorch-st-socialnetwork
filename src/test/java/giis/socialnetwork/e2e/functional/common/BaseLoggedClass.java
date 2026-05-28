package giis.socialnetwork.e2e.functional.common;

import giis.socialnetwork.e2e.functional.utils.Click;
import giis.socialnetwork.e2e.functional.utils.Navigation;
import giis.socialnetwork.e2e.functional.utils.Waiter;
import giis.selema.framework.junit5.LifecycleJunit5;
import giis.selema.manager.SeleManager;
import giis.selema.manager.SelemaConfig;
import giis.selema.services.browser.DynamicGridBrowserService;
import giis.selema.services.impl.WatermarkService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Base class for Social Network browser (Selenium) tests. Manages the Chrome
 * WebDriver lifecycle via Selema and provides helpers for common UI interactions
 * such as filling form fields, logging in, and registering users.
 */
@ExtendWith(LifecycleJunit5.class)
public class BaseLoggedClass {

    protected static final Logger log = LoggerFactory.getLogger(BaseLoggedClass.class);

    private static final SeleManager seleManager = new SeleManager(new SelemaConfig()
            .setReportSubdir("target/containerlogs/"
                    + (System.getProperty("TJOB_NAME") == null ? "" : System.getProperty("TJOB_NAME")))
            .setName(System.getProperty("TJOB_NAME") == null ? "locallogs" : System.getProperty("TJOB_NAME")));

    protected static String sutUrl;
    protected static String tJobName = "DEFAULT_TJOB";
    protected WebDriver driver;
    protected Waiter waiter;
    protected Navigation navUtils;

    @BeforeAll
    static void setupAll() throws IOException {
        log.info("Starting global browser setup");
        Properties properties = new Properties();
        properties.load(Files.newInputStream(Paths.get("src/test/resources/test.properties")));
        tJobName = System.getProperty("TJOB_NAME");
        String envUrl = System.getProperty("SUT_URL") != null
                ? System.getProperty("SUT_URL")
                : System.getenv("SUT_URL");
        sutUrl = envUrl != null ? envUrl : properties.getProperty("LOCALHOST_URL");
        setupBrowser();
        log.info("Global browser setup complete. SUT: {}", sutUrl);
    }

    protected static void setupBrowser() {
        seleManager.setBrowser("chrome").setArguments(new String[]{"--start-maximized", "--incognito"});
        if (System.getenv("SELENOID_PRESENT") != null) {
            log.debug("Setting up Selenium WebDriver with Selenium-hub");
            seleManager.setDriverUrl("http://selenium-hub:4444/wd/hub")
                    .add(new DynamicGridBrowserService().setVideo())
                    .add(new WatermarkService().setDelayOnFailure(3));
        }
    }

    @BeforeEach
    void setup(TestInfo testInfo) {
        log.info("Starting setup for test: {}", testInfo.getDisplayName());
        driver = seleManager.getDriver();
        waiter = new Waiter(driver);
        navUtils = new Navigation(sutUrl);
        driver.get(sutUrl + "/index.html");
        log.info("Setup complete, starting: {}", testInfo.getDisplayName());
    }

    @AfterEach
    void tearDown(TestInfo testInfo) {
        log.info("Tearing down test: {}", testInfo.getDisplayName());
        driver.get(sutUrl + "/index.html");
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    protected void fillField(String name, String value) {
        WebElement field = driver.findElement(By.name(name));
        field.clear();
        field.sendKeys(value);
    }

    /**
     * Submits the login form on index.html with the given credentials and waits
     * for main.html to load. Assumes the browser is already on the login page.
     */
    protected void loginViaForm(String username, String password) throws ElementNotFoundException {
        waiter.waitForLoginPage();
        fillField("username", username);
        fillField("password", password);
        waiter.waitUntil(ExpectedConditions.elementToBeClickable(By.cssSelector("input[name='login']")),
                "Login submit button not clickable");
        Click.element(driver, driver.findElement(By.cssSelector("input[name='login']")));
        waiter.waitForMainPage();
    }

    /**
     * Submits the signup form on signup.html and waits for the redirect back to
     * the login page. Assumes the browser is already on the signup page.
     */
    protected void registerViaForm(String firstName, String lastName,
                                    String username, String password) throws ElementNotFoundException {
        waiter.waitForSignupPage();
        fillField("first_name", firstName);
        fillField("last_name", lastName);
        fillField("username", username);
        fillField("password", password);
        waiter.waitUntil(ExpectedConditions.elementToBeClickable(By.cssSelector("input[name='signup']")),
                "Signup submit button not clickable");
        Click.element(driver, driver.findElement(By.cssSelector("input[name='signup']")));
        waiter.waitForLoginPage();
    }
}
