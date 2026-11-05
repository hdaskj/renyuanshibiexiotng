const proxy = {
  "/api": {
    target: "http://127.0.0.1:8081",
    changeOrigin: true
  }
};

export default {
  server: {
    proxy
  },
  preview: {
    proxy
  }
};
