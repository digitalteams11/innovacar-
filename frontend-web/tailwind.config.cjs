/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#f0f4f8',
          100: '#d9e2ec',
          200: '#bcccdc',
          300: '#9fb3c8',
          400: '#829ab1',
          500: '#1e3a5f',
          600: '#152a45',
          700: '#0f1b3d',
          800: '#0a1229',
          900: '#050a14',
        },
        accent: {
          50: '#fdf8f0',
          100: '#f5e6cc',
          200: '#e8cd9f',
          300: '#d4a853',
          400: '#c9a96e',
          500: '#b8954e',
          600: '#9a7a3d',
        },
        success: {
          50: '#ecfdf5',
          100: '#d1fae5',
          500: '#0f766e',
          600: '#059669',
        },
        warning: {
          50: '#fffbeb',
          100: '#fef3c7',
          500: '#b45309',
          600: '#d97706',
        },
        danger: {
          50: '#fef2f2',
          100: '#fee2e2',
          500: '#be123c',
          600: '#dc2626',
        },
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
      },
      boxShadow: {
        'soft': '0 4px 24px -4px rgba(0, 0, 0, 0.06)',
        'elevated': '0 8px 32px -8px rgba(0, 0, 0, 0.1)',
        'glow': '0 0 24px -4px rgba(30, 58, 95, 0.15)',
      },
    },
  },
  plugins: [],
}
