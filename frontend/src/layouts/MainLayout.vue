<script setup>
import {
  ref,
  computed,
  watch,
  onMounted,
  onBeforeUnmount,
  nextTick,
} from "vue";
import { useRouter, useRoute, RouterView } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore } from "@/stores/auth";
import { getActiveModules } from "@/api/admin";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const isSvg = (val) => typeof val === "string" && val.trim().startsWith("<svg");

// 首页固定为系统导航项，其余从数据库加载
const navItems = ref([
  { path: "/", icon: "🏠", label: "首页", exact: true, isSvgIcon: false },
]);

onMounted(async () => {
  try {
    const modules = await getActiveModules();
    if (Array.isArray(modules)) {
      const dbItems = modules
        .filter((m) => m.path && m.path !== "/")
        .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
        .map((m) => ({
          path: m.path,
          icon: m.icon,
          label: m.title,
          exact: false,
          isSvgIcon: isSvg(m.icon),
        }));
      navItems.value = [...navItems.value, ...dbItems];
    }
  } catch (e) {
    // 加载失败时使用默认导航
  }
});

const isActive = (item) => {
  if (item.exact) return route.path === "/";
  return route.path.startsWith(item.path);
};

const activeIndex = computed(() =>
  navItems.value.findIndex((i) => isActive(i))
);
const isProfileActive = computed(() => route.path === "/profile");

// ========== 悬停追踪 ==========
const hoveredIndex = ref(-1);

const getItemScale = (index) => {
  if (hoveredIndex.value === index) return 2;
  if (activeIndex.value === index) return 1.5;
  return 1;
};

const handleNavClick = (item) => {
  router.push(item.path);
};

// ========== 蓝色药丸流动动画 ==========
const ITEM_HEIGHT = 52;
const ITEM_GAP = 10;
const PADDING_TOP = 16;

const pillTop = computed(() => {
  return PADDING_TOP + activeIndex.value * (ITEM_HEIGHT + ITEM_GAP);
});

const pillMoving = ref(false);
let moveTimer = null;

watch(activeIndex, () => {
  pillMoving.value = true;
  if (moveTimer) clearTimeout(moveTimer);
  moveTimer = setTimeout(() => {
    pillMoving.value = false;
  }, 400);
});

// ========== 用户下拉菜单 ==========
const userDropdownVisible = ref(false);
const userDropdownRef = ref(null);

const toggleUserDropdown = () => {
  userDropdownVisible.value = !userDropdownVisible.value;
};

const userHovered = ref(false);
const getUserAreaScale = () => {
  if (userHovered.value) return 1.3;
  if (isProfileActive.value) return 1.15;
  return 1;
};

const handleDropdownClick = (e) => {
  if (userDropdownRef.value && !userDropdownRef.value.contains(e.target)) {
    userDropdownVisible.value = false;
  }
};

const handleLogout = async () => {
  userDropdownVisible.value = false;
  await authStore.logout();
  ElMessage.success("已退出登录");
  router.push("/login");
};

const goAdmin = () => {
  userDropdownVisible.value = false;
  router.push("/admin");
};

const goProfile = () => {
  userDropdownVisible.value = false;
  router.push("/profile");
};
// ========== 全站入场动画 ==========
const contentAreaRef = ref(null)
let revealObserver = null
const startReveal = async () => {
  await nextTick()
  if (revealObserver) { revealObserver.disconnect(); revealObserver = null }
  const root = contentAreaRef.value
  if (!root) return
  const set = new Set()
  root.querySelectorAll('.modules-section, .module-card, .table-card').forEach((el) => set.add(el))
  revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((en) => {
      if (en.isIntersecting) {
        en.target.classList.add('revealed')
        revealObserver.unobserve(en.target)
      }
    })
  }, { root, threshold: 0.33 })
  // 仅这些"滚动到才出现"的目标初始隐藏，其余内容直接显示
  set.forEach((el) => {
    el.classList.add('reveal-item')
    revealObserver.observe(el)
  })
}
onMounted(() => {
  document.addEventListener("click", handleDropdownClick);
  startReveal()
});
watch(() => route.path, () => startReveal())
onBeforeUnmount(() => {
  document.removeEventListener("click", handleDropdownClick);
  if (moveTimer) clearTimeout(moveTimer);
  if (revealObserver) revealObserver.disconnect();
});
</script>

<template>
  <div class="layout-wrapper">
    <!-- 左侧导航 -->
    <aside class="sidebar">
      <div class="nav-container" @mouseleave="hoveredIndex = -1">
        <transition name="pill-fade">
          <div
            v-if="!isProfileActive"
            class="active-pill"
            :class="{ moving: pillMoving }"
            :style="{ top: pillTop + 'px' }"
          ></div>
        </transition>

        <!-- 导航项 -->
        <div
          v-for="(item, index) in navItems"
          :key="item.path"
          class="nav-item"
          :style="{ height: ITEM_HEIGHT + 'px' }"
          @mouseenter="hoveredIndex = index"
          @click="handleNavClick(item)"
        >
          <div
            class="item-content"
            :style="{ transform: `scale(${getItemScale(index)})` }"
          >
            <span
              v-if="item.isSvgIcon"
              class="nav-icon"
              v-html="item.icon"
            ></span>
            <span v-else class="nav-icon">{{ item.icon }}</span>
          </div>
          <span class="nav-tooltip">{{ item.label }}</span>
        </div>
      </div>
    </aside>

    <!-- 右侧主区域 -->
    <div class="main-area">
      <div class="top-right" ref="userDropdownRef" @mouseleave="userDropdownVisible = false">
        <div class="site-title">气象数据可视化平台</div>
        <div
          class="user-area"
          :class="{ active: isProfileActive }"
          :style="{ transform: `scale(${getUserAreaScale()})` }"
          @mouseenter="userHovered = true"
          @mouseleave="userHovered = false"
          @click.stop="toggleUserDropdown"
        >
          <img
            v-if="authStore.user?.avatar"
            class="user-avatar-img"
            :src="authStore.user.avatar"
            alt="头像"
          />
          <span v-else class="user-avatar">👤</span>
          <span class="user-name">{{ authStore.username }}</span>
          <span
            v-if="authStore.isAdmin"
            class="role-badge admin"
            >管理员</span
          >
          <span class="dropdown-arrow">▾</span>
        </div>

        <transition name="dropdown-fade">
          <div v-if="userDropdownVisible" class="user-dropdown">
            <div v-if="authStore.isAdmin" class="dropdown-item" @click="goAdmin">
              <span class="dropdown-icon">⚙️</span>
              <span>后台管理</span>
            </div>
            <div v-else class="dropdown-item" @click="goProfile">
              <span class="dropdown-icon">👤</span>
              <span>个人中心</span>
            </div>
            <div class="dropdown-item" @click="handleLogout">
              <span class="dropdown-icon">🚪</span>
              <span>退出登录</span>
            </div>
          </div>
        </transition>
      </div>

      <main class="content-area" ref="contentAreaRef">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout-wrapper {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #f8fafc;
}

/* ========== 左侧导航 ========== */
.sidebar {
  padding: 0 0 0 24px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex-shrink: 0;
  z-index: 100;
}

/* 导航容器：白色弧形背景，固定宽度不随悬停扩展 */
.nav-container {
  position: relative;
  background: #ffffff;
  border-radius: 48px;
  padding: 16px 12px;
  width: 76px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 蓝色流动药丸 */
.active-pill {
  position: absolute;
  left: 12px;
  width: 52px;
  height: 52px;
  border-radius: 20px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  transition: top 0.4s cubic-bezier(0.4, 0, 0.2, 1),
    transform 0.4s cubic-bezier(0.4, 0, 0.2, 1), border-radius 0.4s ease;
  z-index: 0;
}

/* 流动过程中缩小 */
.active-pill.moving {
  transform: scaleY(0.8);
}

.pill-fade-enter-active,
.pill-fade-leave-active {
  transition: opacity 0.3s ease;
}
.pill-fade-enter-from,
.pill-fade-leave-to {
  opacity: 0;
}

/* 导航项 */
.nav-item {
  position: relative;
  width: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 1;
}

/* 图标缩放动画 */
.item-content {
  display: flex;
  align-items: center;
  justify-content: center;
  transform-origin: center center;
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  will-change: transform;
}

.nav-icon {
  font-size: 22px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.nav-icon :deep(svg) {
  width: 22px;
  height: 22px;
  fill: currentColor;
  stroke: currentColor;
  flex-shrink: 0;
}

/* 悬停气泡提示 */
.nav-tooltip {
  position: absolute;
  left: calc(100% + 14px);
  top: 50%;
  transform: translateY(-50%) translateX(-6px);
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  padding: 6px 14px;
  border-radius: 4px;
  background: #374151;
  color: #ffffff;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.25s ease, transform 0.25s ease;
  z-index: 200;
}

.nav-item:hover .nav-tooltip {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
}

/* ========== 右侧主区域 ========== */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.top-right {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 20px 32px 0;
  position: relative;
  z-index: 50;
}

.site-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  letter-spacing: 1px;
  pointer-events: none;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 24px;
  cursor: pointer;
  background: #ffffff;
  transition: background 0.3s ease, color 0.3s ease,
    transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.user-area.active {
  background: #3b82f6;
  animation: activePop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}
@keyframes activePop {
  0% { transform: scale(0.95); }
  60% { transform: scale(1.2); }
  100% { transform: scale(1.15); }
}
.user-area.active .user-name,
.user-area.active .dropdown-arrow,
.user-area.active .user-avatar {
  color: #fff;
}

.user-avatar {
  font-size: 18px;
}
.user-avatar-img {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}

.user-name {
  font-size: 14px;
  color: #374151;
  font-weight: 500;
}

.role-badge {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
  background: #f3f4f6;
  color: #6b7280;
  border: 1px solid #e5e7eb;
}

.role-badge.admin {
  background: #fef2f2;
  color: #ef4444;
  border-color: #fecaca;
  cursor: pointer;
  transition: all 0.2s;
}

.role-badge.admin:hover {
  background: #fee2e2;
}

.dropdown-arrow {
  font-size: 10px;
  color: #9ca3af;
  margin-left: 2px;
}

.user-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 32px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  min-width: 140px;
  padding: 6px 0;
  z-index: 200;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 14px;
  color: #374151;
  cursor: pointer;
  transition: background 0.15s;
}

.dropdown-item:hover {
  background: #f3f4f6;
  color: #ef4444;
}

.dropdown-icon {
  font-size: 16px;
}

.dropdown-fade-enter-active,
.dropdown-fade-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}

.dropdown-fade-enter-from,
.dropdown-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.content-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
