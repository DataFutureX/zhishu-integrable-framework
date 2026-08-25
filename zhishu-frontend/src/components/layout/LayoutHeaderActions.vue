<template>
  <div class="header-actions" :class="{ 'header-actions--primary': onPrimary }">
    <el-tooltip content="产品门户" placement="bottom" :show-after="400">
      <button
        type="button"
        class="portal-entry"
        :class="{ 'portal-entry--on-primary': onPrimary }"
        aria-label="Portal"
        @click="goPortal"
      >
        <el-icon :size="16"><HomeFilled /></el-icon>
        <span>Portal</span>
      </button>
    </el-tooltip>
    <NotificationBell :on-primary="onPrimary" />
    <LayoutSettings :on-primary="onPrimary" />
    <el-dropdown @command="(command: string) => emit('command', command)">
      <div class="user-info">
        <el-avatar :size="32" class="user-avatar" :icon="UserFilled" />
        <span class="username">{{ userName }}</span>
      </div>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="profile">
            <el-icon><User /></el-icon>
            个人信息
          </el-dropdown-item>
          <el-dropdown-item command="password">
            <el-icon><Lock /></el-icon>
            修改密码
          </el-dropdown-item>
          <el-dropdown-item command="logout" divided>
            <el-icon><SwitchButton /></el-icon>
            退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeFilled, User, UserFilled, Lock, SwitchButton } from '@element-plus/icons-vue'
import LayoutSettings from '@/components/layout/LayoutSettings.vue'
import NotificationBell from '@/components/layout/NotificationBell.vue'

defineProps<{
  userName: string
  onPrimary?: boolean
}>()

const emit = defineEmits<{
  command: [value: string]
}>()

const router = useRouter()

const goPortal = () => {
  router.push('/portal')
}
</script>

<style scoped lang="scss">
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.portal-entry {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  color: var(--app-text-regular, #606266);
  font-family: 'Outfit', 'Noto Sans SC', sans-serif;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  cursor: pointer;
  transition:
    color 0.2s ease,
    background 0.2s ease,
    border-color 0.2s ease;

  &:hover {
    color: var(--app-primary, #0969da);
    background: color-mix(in srgb, var(--app-primary, #0969da) 10%, transparent);
    border-color: color-mix(in srgb, var(--app-primary, #0969da) 18%, transparent);
  }

  &--on-primary {
    color: rgba(255, 255, 255, 0.92);
    border-color: rgba(255, 255, 255, 0.18);
    background: rgba(255, 255, 255, 0.08);

    &:hover {
      color: #fff;
      background: rgba(255, 255, 255, 0.16);
      border-color: rgba(255, 255, 255, 0.28);
    }
  }
}

.user-avatar {
  flex-shrink: 0;
  --el-avatar-bg-color: #e8eef4;
  --el-avatar-text-size: 16px;
  background: #e8eef4;
  color: #5b6b7c;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border-radius: 999px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.22s ease;

  &:hover {
    background-color: color-mix(in srgb, var(--app-primary) 8%, transparent);

    .username {
      color: var(--app-primary);
    }
  }

  .username {
    font-size: 13px;
    font-weight: 500;
    color: var(--app-text-primary);
    transition: color 0.22s ease;
    max-width: 96px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.header-actions--primary {
  gap: 6px;

  .user-info {
    border-color: rgba(255, 255, 255, 0.18);
    background: rgba(255, 255, 255, 0.08);

    &:hover {
      background: rgba(255, 255, 255, 0.16);
      border-color: rgba(255, 255, 255, 0.28);

      .username {
        color: #fff;
      }
    }

    .username {
      color: rgba(255, 255, 255, 0.92);
    }

    .user-avatar {
      --el-avatar-bg-color: rgba(255, 255, 255, 0.22);
      background: rgba(255, 255, 255, 0.22);
      color: #fff;
    }
  }
}
</style>
