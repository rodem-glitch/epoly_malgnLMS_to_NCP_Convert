import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss()],
  esbuild: {
    // 왜: Resin 정적파일 응답의 charset이 환경마다 달라서 한글이 깨지는 경우가 있습니다.
    //     빌드 산출물을 ASCII escape(\\uXXXX)로 고정하면, 서버 charset과 무관하게 한글이 안전하게 표시됩니다.
    charset: 'ascii',
  },
  build: {
    outDir: '../public_html/tutor_lms/app',
    emptyOutDir: true,
  },
  server: {
    // 왜: `npm run dev`(Vite)로 교수자 LMS를 열면, API(`/tutor_lms/api/*`)는 Resin(8080)으로 가야 합니다.
    //     프록시가 없으면 Vite가 HTML(index.html/404)을 반환해서 “서버 응답이 JSON이 아닙니다”가 뜹니다.
    proxy: {
      '/tutor_lms/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    // 왜: `npm run preview`에서도 동일하게 API는 Resin으로 보내야 합니다.
    proxy: {
      '/tutor_lms/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
