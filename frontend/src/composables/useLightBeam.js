import { ref, onMounted, onBeforeUnmount } from 'vue'

// 登录 / 注册左侧品牌区共用的鼠标跟随光柱动画
// 由两个页面原先完全重复的 onMounted 块抽取而来，行为保持一致
export function useLightBeam() {
  const canvasRef = ref(null)
  let animId = null
  let mouseAngle = Math.PI * 0.25

  onMounted(() => {
    const canvas = canvasRef.value
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    let W, H
    function resize() {
      W = canvas.width = canvas.offsetWidth
      H = canvas.height = canvas.offsetHeight
    }
    resize()
    window.addEventListener('resize', resize)

    const onMove = (e) => {
      const rect = canvas.getBoundingClientRect()
      mouseAngle = Math.atan2(e.clientY - rect.top, e.clientX - rect.left)
    }
    canvas.parentElement.addEventListener('mousemove', onMove)

    function draw() {
      ctx.clearRect(0, 0, W, H)
      const spread = 0.15
      const maxR = Math.max(W, H) * 1.5
      ctx.beginPath()
      ctx.moveTo(0, 0)
      for (let a = mouseAngle - spread; a <= mouseAngle + spread; a += 0.02) {
        ctx.lineTo(Math.cos(a) * maxR, Math.sin(a) * maxR)
      }
      ctx.closePath()
      const g = ctx.createLinearGradient(
        0, 0,
        Math.cos(mouseAngle) * W, Math.sin(mouseAngle) * H
      )
      g.addColorStop(0, 'rgba(255,240,200,0.45)')
      g.addColorStop(0.5, 'rgba(255,240,200,0.12)')
      g.addColorStop(1, 'rgba(255,240,200,0)')
      ctx.fillStyle = g
      ctx.fill()
      animId = requestAnimationFrame(draw)
    }
    draw()
  })

  onBeforeUnmount(() => {
    if (animId) cancelAnimationFrame(animId)
  })

  return { canvasRef }
}
