package com.orangefunction.tomcat.redissessions;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * User: Kai Inkinen <kai.inkinen@futurice.com>
 * Date: 2015.05.04
 * Time: 10:15
 */
public class RedisSessionManagerTest {

    private RedisSessionManager manager;
    private String[] randomJvmRoutes = new String[]{"abc", "123", "abc123", "......", "1.1.2.jvmRoute", ",.,..,.,"};

    @Before
    public final void setupRedisSessionManagerTest() {
        manager = new RedisSessionManager();
    }

    @Test
    public void sessionIdWithoutJvmRouteIsReturned() {
        assertEquals("session", manager.stripJvmRoute("session", "jvmRoute"));
    }

    @Test
    public void nullReturnedAsIs() {
        assertNull(manager.stripJvmRoute(null, "jvmRoute"));
        assertNull(manager.stripJvmRoute(null, null));
    }

    @Test
    public void sessionIdIsNotModifiedIfWeDontHaveAJvmRoute() {
        for (String jvmRoute : randomJvmRoutes) {
            assertEquals("session." + jvmRoute, manager.stripJvmRoute("session." + jvmRoute, null));
        }
    }

    @Test
    public void sessionIdWithAnyJvmRouteIsModified() {
        for (String jvmRoute : randomJvmRoutes) {
            assertEquals("session", manager.stripJvmRoute("session." + jvmRoute, "unknown" + jvmRoute));
        }
    }

    @Test
    public void generatedSessionStripsJvmRoute() {
        for (String jvmRoute : randomJvmRoutes) {
            assertEquals("session", manager.stripJvmRoute("session." + jvmRoute, jvmRoute));
        }
    }

}
