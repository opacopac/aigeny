import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    include:  ['src/test/js/**/*.test.js'],
    exclude:  ['src/test/js/write-mode.test.js', 'src/test/js/write-mode-css.test.js'],
  },
});

