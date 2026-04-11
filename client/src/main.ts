import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './styles/index.css'

import App from './App.vue'
import router from './router'
import { useUserStore } from './store/user'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

const userStore = useUserStore()
userStore.restoreFromStorage()

app.mount('#app')
