<script setup>
import { useLightBeam } from '@/composables/useLightBeam'

// 登录 / 注册共用的左侧品牌区：标题、功能点、鼠标光柱动画
const { canvasRef } = useLightBeam()
</script>

<template>
  <div class="left-panel">
    <canvas ref="canvasRef" class="light-canvas"></canvas>
    <div class="left-content">
      <h1 class="brand-title">气象数据可视化平台</h1>
      <p class="brand-sub">实时天气 · 历史数据 · 智能分析</p>
      <div class="feat-list">
        <div class="feat-item">◆ 和风天气实时数据</div>
        <div class="feat-item">◆ 2000-2020 历史数据可视化</div>
        <div class="feat-item">◆ 全国地图天气看板</div>
        <div class="feat-item">◆ AI 天气问答助手</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.left-panel {
  flex: 1;
  position: relative;
  background: linear-gradient(135deg, #4facfe 0%, #f6d365 100%);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}
.light-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.left-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 40px;
}
.brand-title {
  font-size: 32px;
  margin-bottom: 12px;
  font-weight: 600;
}
.brand-sub {
  font-size: 15px;
  opacity: 0.9;
  margin-bottom: 32px;
}
.feat-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
  width: fit-content;
  margin: 0 auto;
}
.feat-item {
  font-size: 14px;
  opacity: 0.9;
}
@media (max-width: 900px) {
  .left-panel { display: none; }
}
</style>
