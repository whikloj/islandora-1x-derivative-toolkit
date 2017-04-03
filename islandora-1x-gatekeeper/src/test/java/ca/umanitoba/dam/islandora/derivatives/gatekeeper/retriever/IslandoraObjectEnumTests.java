package ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever;

import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.checksumTypes.DISABLED;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.checksumTypes.MD5;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.checksumTypes.SHA1;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.checksumTypes.SHA256;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.checksumTypes.SHA385;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.checksumTypes.SHA512;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.controlGroups.ExternallyReferencedContent;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.controlGroups.InternalXmlContent;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.controlGroups.ManagedContent;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.controlGroups.RedirectReferencedContent;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraObject.FedoraStates.Active;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraObject.FedoraStates.Deleted;
import static ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraObject.FedoraStates.Inactive;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.checksumTypes;
import ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraDatastream.controlGroups;
import ca.umanitoba.dam.islandora.derivatives.gatekeeper.retriever.IslandoraObject.FedoraStates;

@RunWith(MockitoJUnitRunner.class)
public class IslandoraObjectEnumTests {

    protected checksumTypes checksum;

    protected FedoraStates state;

    protected controlGroups group;

    @Test
    public void testValidChecksumTypes() throws Exception {
        checksum = checksumTypes.fromString("MD5");
        assertEquals("Did not map to correct value", MD5, checksum);

        checksum = checksumTypes.fromString("SHA-1");
        assertEquals("Did not map to correct value", SHA1, checksum);

        checksum = checksumTypes.fromString("SHA-256");
        assertEquals("Did not map to correct value", SHA256, checksum);

        checksum = checksumTypes.fromString("SHA-385");
        assertEquals("Did not map to correct value", SHA385, checksum);

        checksum = checksumTypes.fromString("SHA-512");
        assertEquals("Did not map to correct value", SHA512, checksum);

    }

    @Test
    public void testDisabledChecksumType() throws Exception {
        checksum = checksumTypes.fromString(null);
        assertEquals("Did not map to correct value", DISABLED, checksum);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidChecksumType() throws Exception {
        checksum = checksumTypes.fromString("uh-oh");
    }

    @Test
    public void testValidStatesActive() throws Exception {
        state = FedoraStates.fromString("A");
        assertEquals("Did not map to correct State", Active, state);
        state = FedoraStates.fromChar('A');
        assertEquals("Did not map to correct State", Active, state);
        state = FedoraStates.fromString("Active");
        assertEquals("Did not map to correct State", Active, state);
    }

    @Test
    public void testValidStatesInactive() throws Exception {
        state = FedoraStates.fromString("I");
        assertEquals("Did not map to correct State", Inactive, state);
        state = FedoraStates.fromChar('I');
        assertEquals("Did not map to correct State", Inactive, state);
        state = FedoraStates.fromString("Inactive");
        assertEquals("Did not map to correct State", Inactive, state);
    }

    @Test
    public void testValidStatesDeleted() throws Exception {
        state = FedoraStates.fromString("D");
        assertEquals("Did not map to correct State", Deleted, state);
        state = FedoraStates.fromChar('D');
        assertEquals("Did not map to correct State", Deleted, state);
        state = FedoraStates.fromString("Deleted");
        assertEquals("Did not map to correct State", Deleted, state);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidStateNull() throws Exception {
        state = FedoraStates.fromString(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidStateString() throws Exception {
        state = FedoraStates.fromString("Missing");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidStateCase() throws Exception {
        state = FedoraStates.fromChar('a');
    }

    @Test
    public void testValidControlGroups() throws Exception {
        group = controlGroups.fromString("X");
        assertEquals("Did not map to the correct Control Group",
                InternalXmlContent, group);

        group = controlGroups.fromString("M");
        assertEquals("Did not map to the correct Control Group", ManagedContent,
                group);

        group = controlGroups.fromString("E");
        assertEquals("Did not map to the correct Control Group",
                ExternallyReferencedContent, group);

        group = controlGroups.fromString("R");
        assertEquals("Did not map to the correct Control Group",
                RedirectReferencedContent, group);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidControlGroupsNull() throws Exception {
        group = controlGroups.fromString(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidControlGroups() throws Exception {
        group = controlGroups.fromString("G");
    }
}
