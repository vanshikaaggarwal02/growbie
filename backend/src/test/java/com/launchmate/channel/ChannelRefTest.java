package com.launchmate.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChannelRefTest {

    @Test
    void readsHandleLinksWithOrWithoutTrailingPages() {
        for (String url : new String[] {
                "https://www.youtube.com/@GoogleDevelopers", "https://youtube.com/@GoogleDevelopers",
                "https://m.youtube.com/@GoogleDevelopers/videos", "https://www.youtube.com/@GoogleDevelopers/shorts",
                "  https://www.youtube.com/@GoogleDevelopers?si=abc  "}) {
            assertEquals(new ChannelRef.Handle("GoogleDevelopers"), ChannelRef.parse(url), url);
        }
        assertEquals(new ChannelRef.Handle("a.b_c-d"), ChannelRef.parse("https://www.youtube.com/@a.b_c-d"));
    }

    @Test
    void readsChannelIdAndLegacyUserLinks() {
        assertEquals(new ChannelRef.Id("UC_x5XG1OV2P6uZZ5FSM9Ttw"),
                ChannelRef.parse("https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw"));
        assertEquals(new ChannelRef.Id("UC_x5XG1OV2P6uZZ5FSM9Ttw"),
                ChannelRef.parse("https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw/videos"));
        assertEquals(new ChannelRef.Username("oldname"), ChannelRef.parse("https://www.youtube.com/user/oldname"));
    }

    @Test
    void customLinksAreTriedAsAHandleFirst() {
        assertEquals(new ChannelRef.Custom("SomeCreator"), ChannelRef.parse("https://www.youtube.com/c/SomeCreator"));
        assertEquals(new ChannelRef.Custom("mkbhd"), ChannelRef.parse("https://www.youtube.com/mkbhd"));
        assertEquals("https://www.youtube.com/watch", assertThrows(ChannelRef.Unsupported.class,
                () -> ChannelRef.parse("https://www.youtube.com/watch")).getMessage().isEmpty() ? "" : "https://www.youtube.com/watch");
    }

    @Test
    void lenientInputIsNormalisedToAnHttpsLink() {
        assertEquals(new ChannelRef.Handle("mkbhd"), ChannelRef.parse("@mkbhd"));
        assertEquals(new ChannelRef.Handle("mkbhd"), ChannelRef.parse("www.youtube.com/@mkbhd"));
        assertEquals(new ChannelRef.Handle("mkbhd"), ChannelRef.parse("http://www.youtube.com/@mkbhd/videos"));
        assertEquals(new ChannelRef.Handle("MrBeast"), ChannelRef.parse("youtube.com/@MrBeast/shorts"));
    }

    @Test
    void videoLinksGetAHelpfulMessage() {
        ChannelRef.Unsupported e = assertThrows(ChannelRef.Unsupported.class,
                () -> ChannelRef.parse("https://youtu.be/dQw4w9WgXcQ"));
        assertTrue(e.getMessage().contains("video link"), e.getMessage());
    }

    @Test
    void everythingElseIsRefused() {
        for (String bad : new String[] {
                null, "", "   ", "https://example.com/@abc",
                "https://youtube.com.evil.example/@abc", "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "https://www.youtube.com/playlist?list=PL123", "https://www.youtube.com/", "https://youtu.be/dQw4w9WgXcQ",
                "https://www.youtube.com/@ab", "https://www.youtube.com/@bad handle",
                "https://www.youtube.com/channel/notAChannelId", "ftp://www.youtube.com/@abc"}) {
            assertThrows(ChannelRef.Unsupported.class, () -> ChannelRef.parse(bad), "should refuse: " + bad);
        }
    }
}
