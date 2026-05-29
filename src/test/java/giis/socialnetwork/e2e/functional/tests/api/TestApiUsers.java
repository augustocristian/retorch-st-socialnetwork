package giis.socialnetwork.e2e.functional.tests.api;

import giis.socialnetwork.e2e.functional.common.BaseApiClass;
import giis.retorch.annotations.AccessMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Validates the user-service endpoints exposed through the Nginx gateway:
 * <ul>
 *   <li>POST /api/user/register — register a new user (HTTP 200 after redirect)</li>
 *   <li>POST /api/user/login   — authenticate and receive JWT cookie (HTTP 200 after redirect)</li>
 * </ul>
 */
class TestApiUsers extends BaseApiClass {

    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("POST /api/user/register returns HTTP 200 and accepts the new user")
    void testRegisterUser() throws IOException {
        long ts = unique();
        String username = "reg" + ts;

        int status = registerUser("Alice", "Reg", username, "pwd" + ts);
        Assertions.assertEquals(200, status, "Registration must return HTTP 200 (redirect followed)");
    }

    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("POST /api/user/login returns HTTP 200 and sets the login_token JWT cookie")
    void testLoginUser() throws IOException {
        long ts = unique();
        String username = "log" + ts;
        String password = "pwd" + ts;

        registerUser("Bob", "Login", username, password);

        long userId = loginUser(username, password);
        Assertions.assertTrue(userId > 0, "Parsed user_id from JWT must be a positive integer");
    }
}
