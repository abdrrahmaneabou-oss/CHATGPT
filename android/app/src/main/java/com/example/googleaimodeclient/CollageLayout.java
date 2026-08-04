package com.example.googleaimodeclient;

/** Pure layout math for the numbered image board. */
final class CollageLayout {
    final int count;
    final int columns;
    final int rows;
    final int tileSize;
    final int gap;
    final int headerHeight;
    final int labelHeight;
    final int width;
    final int height;

    private CollageLayout(
            int count,
            int columns,
            int tileSize,
            int gap,
            int headerHeight,
            int labelHeight
    ) {
        this.count = count;
        this.columns = columns;
        this.rows = (int) Math.ceil(count / (double) columns);
        this.tileSize = tileSize;
        this.gap = gap;
        this.headerHeight = headerHeight;
        this.labelHeight = labelHeight;
        this.width = columns * tileSize + (columns + 1) * gap;
        this.height = headerHeight + rows * (tileSize + labelHeight) + (rows + 1) * gap;
    }

    static CollageLayout forCount(int count) {
        if (count < 1 || count > 5) {
            throw new IllegalArgumentException("Image count must be between 1 and 5");
        }
        if (count == 1) {
            return new CollageLayout(count, 1, 1280, 32, 96, 72);
        }
        if (count <= 4) {
            return new CollageLayout(count, 2, 820, 24, 88, 64);
        }
        return new CollageLayout(count, 3, 600, 20, 82, 58);
    }

    Tile tileAt(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("No image at index " + index);
        }
        int row = index / columns;
        int column = index % columns;
        int firstIndexInRow = row * columns;
        int itemsInRow = Math.min(columns, count - firstIndexInRow);
        int rowWidth = itemsInRow * tileSize + Math.max(0, itemsInRow - 1) * gap;
        int rowStart = (width - rowWidth) / 2;
        int left = rowStart + column * (tileSize + gap);
        int top = headerHeight + gap + row * (tileSize + labelHeight + gap);
        return new Tile(left, top, left + tileSize, top + tileSize);
    }

    static final class Tile {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Tile(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }
}

