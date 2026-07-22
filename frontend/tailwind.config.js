/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        surface: {
          950: "#0b0f17",
          900: "#111827",
          850: "#161e2e",
          800: "#1f2937",
          700: "#374151",
        },
      },
    },
  },
  plugins: [],
};
