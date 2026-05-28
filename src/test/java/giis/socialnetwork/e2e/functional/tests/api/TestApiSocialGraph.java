package giis.socialnetwork.e2e.functional.tests.api;

import com.google.gson.JsonArray;
import giis.socialnetwork.e2e.functional.common.BaseApiClass;
import giis.retorch.annotations.AccessMode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Validates the social-graph endpoints exposed through the Nginx gateway:
 * <ul>
 *   <li>POST /api/user/follow       — follow a user by username (HTTP 200 after redirect)</li>
 *   <li>GET  /api/user/get_follower — list followers of the logged-in user (JWT auth)</li>
 * </ul>
 */
class TestApiSocialGraph extends BaseApiClass {

    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "social-graph", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("POST /api/user/follow returns HTTP 200 when following by username")
    void testFollowUser() throws IOException {
        String[] userA = createUserWithName("follower");
        String[] userB = createUserWithName("followee");

        int status = followUser(userA[0], userB[0]);
        Assertions.assertEquals(200, status, "Follow must return HTTP 200 (redirect to contact.html followed)");
    }

    @AccessMode(resID = "user", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @AccessMode(resID = "social-graph", concurrency = 1, sharing = false, accessMode = "READWRITE")
    @Test
    @DisplayName("GET /api/user/get_follower returns JSON array containing the follower after a follow action")
    void testGetFollowers() throws IOException {
        // Register User A and capture their user_id
        String[] userA = createUserWithName("fa");
        long userAId = Long.parseLong(userA[1]);

        // Register User B (login as B so the cookie is set to B's session)
        String[] userB = createUserWithName("fb");

        // A follows B by username — no auth required for this endpoint
        followUser(userA[0], userB[0]);

        // Re-login as B so the cookie store holds B's JWT (get_follower reads user_id from JWT)
        loginUser(userB[0], userB[2]);

        JsonArray followers = getJsonArray(userUrl("/get_follower"));
        Assertions.assertFalse(followers.isEmpty(),
                "B's follower list must not be empty after A follows B");

        boolean foundA = false;
        for (com.google.gson.JsonElement elem : followers) {
            if (elem.isJsonObject()
                    && String.valueOf(userAId).equals(elem.getAsJsonObject().get("follower_id").getAsString())) {
                foundA = true;
                break;
            }
        }
        Assertions.assertTrue(foundA,
                "User A (id=" + userAId + ") must appear in B's follower list");
    }
}
