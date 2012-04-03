package hudson.plugins.space_left;

import junit.framework.TestCase;

/**
 * Created by IntelliJ IDEA.
 * User: jet
 * Date: 3/20/12
 * Time: 12:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class SpaceLeftPropertyTest extends TestCase {
    public void testGetSpaceNeeded() throws Exception {
        SpaceLeftProperty spaceLeftProperty = new SpaceLeftProperty();

        spaceLeftProperty.setRequiredSpace(1L);

        spaceLeftProperty.setFactor(1.23);

        assertEquals(1, spaceLeftProperty.getSpaceNeeded());

        spaceLeftProperty.setRequiredSpace(100L);

        assertEquals(123, spaceLeftProperty.getSpaceNeeded());
    }
}
