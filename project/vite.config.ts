import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig(({ mode }) => ({
  base: './',
  plugins: [react(), tailwindcss()],
  esbuild: {
    // 왜: 서버 charset과 무관하게 한글이 안전하게 표시되도록 ASCII escape(\\uXXXX)로 고정합니다.
    charset: 'ascii',
    // 왜: 프로덕션 빌드에서 console.log, debugger 제거 (정보 노출 방지)
    drop: mode === 'production' ? ['console', 'debugger'] : [],
  },
  build: {
    outDir: '../public_html/tutor_lms/app',
    emptyOutDir: true,
    // 왜: 소스맵 비활성화 (소스 코드 노출 방지)
    sourcemap: false,
    // 왜: 빌드 크기 경고 임계값
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // 왜: 파일명에 해시 추가 (캐시 무효화 + 추측 방지)
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash].[ext]',
      },
    },
  },
  // 왜: 개발 서버 보안 설정
  server: {
    // 왜: 외부 접근 차단 (개발 환경)
    host: 'localhost',
    // 왜: CORS 제한
    cors: false,
  },
}));
