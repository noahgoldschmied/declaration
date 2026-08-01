/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Rye"', "serif"],
        body: ['"Arvo"', "Georgia", "serif"],
      },
    },
  },
  plugins: [],
};
