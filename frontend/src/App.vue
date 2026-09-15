<script setup>
import { RouterView } from 'vue-router'
import ChatPanel from '@/components/ChatPanel.vue'
</script>

<template>
  <RouterView />
  <!-- 全局悬浮天气智能助手（登录页/注册页不显示） -->
  <ChatPanel />
</template>

<style>
#app {
  width: 100%;
  min-height: 100vh;
}
</style>
