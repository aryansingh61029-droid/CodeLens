/** Scan HTML and DOM-generated UI classes when producing the bundled stylesheet. */
module.exports = {
  content: ['./src/main/resources/static/*.html', './src/main/resources/static/*.js'],
  theme: { extend: {} },
  plugins: []
};
