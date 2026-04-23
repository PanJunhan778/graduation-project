import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      dts: false,
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
    Components({
      dts: false,
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, '/')

          if (!normalizedId.includes('/node_modules/')) {
            return
          }

          if (
            normalizedId.includes('/node_modules/vue/') ||
            normalizedId.includes('/node_modules/vue-router/') ||
            normalizedId.includes('/node_modules/pinia/')
          ) {
            return 'vue-vendor'
          }

          if (
            normalizedId.includes('/node_modules/@element-plus/icons-vue/')
          ) {
            return 'element-plus-icons'
          }

          if (normalizedId.includes('/node_modules/element-plus/')) {
            const componentMatch = normalizedId.match(/\/element-plus\/es\/components\/([^/]+)\//)
            const componentName = componentMatch?.[1]

            if (
              componentName &&
              [
                'form',
                'form-item',
                'input',
                'input-number',
                'select',
                'option',
                'date-picker',
                'checkbox',
                'checkbox-group',
                'radio',
                'radio-group',
                'upload',
                'pagination',
                'dialog',
                'drawer',
              ].includes(componentName)
            ) {
              return 'element-plus-form'
            }

            if (
              componentName &&
              [
                'table',
                'table-column',
                'card',
                'tag',
                'empty',
                'descriptions',
                'descriptions-item',
                'progress',
                'statistic',
                'skeleton',
                'skeleton-item',
              ].includes(componentName)
            ) {
              return 'element-plus-data'
            }

            if (
              componentName &&
              [
                'message',
                'message-box',
                'loading',
                'tooltip',
                'popover',
                'dropdown',
                'dropdown-menu',
                'dropdown-item',
                'popconfirm',
              ].includes(componentName)
            ) {
              return 'element-plus-feedback'
            }

            return 'element-plus-core'
          }

          if (
            normalizedId.includes('/node_modules/@floating-ui/') ||
            normalizedId.includes('/node_modules/@popperjs/core/')
          ) {
            return 'element-plus-feedback'
          }

          if (normalizedId.includes('/node_modules/async-validator/')) {
            return 'element-plus-form'
          }

          if (
            normalizedId.includes('/node_modules/zrender/') ||
            normalizedId.includes('/node_modules/echarts/node_modules/zrender/')
          ) {
            return 'zrender'
          }

          if (
            normalizedId.includes('/node_modules/echarts/charts.js') ||
            normalizedId.includes('/node_modules/echarts/lib/chart/')
          ) {
            return 'echarts-chart'
          }

          if (
            normalizedId.includes('/node_modules/echarts/components.js') ||
            normalizedId.includes('/node_modules/echarts/lib/component/')
          ) {
            return 'echarts-component'
          }

          if (
            normalizedId.includes('/node_modules/echarts/renderers.js') ||
            normalizedId.includes('/node_modules/echarts/lib/renderer/')
          ) {
            return 'echarts-renderer'
          }

          if (normalizedId.includes('/node_modules/echarts/')) {
            return 'echarts-core'
          }

          if (normalizedId.includes('/node_modules/jspdf/')) {
            return 'jspdf'
          }

          if (normalizedId.includes('/node_modules/html2canvas/')) {
            return 'html2canvas'
          }
        },
      },
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
