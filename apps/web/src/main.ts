import { createApp } from 'vue'
import './style.css'

const publicView = window.location.pathname.startsWith('/exhibits')
const Root = publicView
  ? (await import('./PublicApp.vue')).default
  : (await import('./App.vue')).default

createApp(Root).mount('#app')
