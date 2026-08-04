package com.example.googleaimodeclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CollageLayoutTest {
    @Test
    public void oneImageUsesLargeSingleTile() {
        CollageLayout layout = CollageLayout.forCount(1);

        assertEquals(1, layout.columns);
        assertEquals(1, layout.rows);
        assertEquals(1280, layout.tileAt(0).width());
    }

    @Test
    public void fourImagesUseBalancedGrid() {
        CollageLayout layout = CollageLayout.forCount(4);

        assertEquals(2, layout.columns);
        assertEquals(2, layout.rows);
        assertEquals(layout.tileAt(0).left, layout.tileAt(2).left);
        assertEquals(layout.tileAt(1).left, layout.tileAt(3).left);
    }

    @Test
    public void lastRowIsCenteredWhenItIsNotFull() {
        CollageLayout layout = CollageLayout.forCount(5);
        CollageLayout.Tile fourth = layout.tileAt(3);
        CollageLayout.Tile fifth = layout.tileAt(4);

        int leftMargin = fourth.left;
        int rightMargin = layout.width - fifth.right;
        assertEquals(leftMargin, rightMargin);
        assertTrue(fourth.left > layout.gap);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMoreThanFiveImages() {
        CollageLayout.forCount(6);
    }
}
