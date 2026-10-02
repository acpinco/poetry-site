import js from "@eslint/js";
import { defineConfig, globalIgnores } from "eslint/config";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

export default defineConfig([
  globalIgnores(["dist"]),
  {
    files: ["src/**/*.{ts,tsx}", "*.ts"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
    ],
    languageOptions: { globals: globals.browser },
  },
  {
    files: ["public/sw.js"],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.serviceworker },
  },
  {
    files: ["eslint.config.js"],
    languageOptions: { globals: globals.node },
  },
]);
