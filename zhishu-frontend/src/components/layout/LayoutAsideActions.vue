<template>
  <div class="aside-actions" :class="{ 'aside-actions--collapse': collapse }">
    <el-dropdown
      class="aside-actions__user"
      :placement="collapse ? 'right-start' : 'top-start'"
      @command="(command: string) => emit('command', command)"
    >
      <div class="aside-user" :title="collapse ? userName : undefined">
        <el-avatar :size="30" class="aside-user__avatar" :icon="UserFilled" />
        <template v-if="!collapse">
          <span class="aside-user__name">{{ userName }}</span>
          <el-icon class="aside-user__caret"><ArrowUp /></el-icon>
        </template>
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

    <div class="aside-actions__row">
      <el-tooltip content="产品门户" :placement="tooltipPlacement" :show-after="400">
        <button
          type="button"
          class="aside-action-btn"
          aria-label="Portal"
          @click="goPortal"
        >
          <el-icon :size="16"><HomeFilled /></el-icon>
        </button>
      </el-tooltip>

      <NotificationBell :placement="popoverPlacement" />
      <LayoutSettings :placement="popoverPlacement" />

      <el-tooltip
        :content="collapse ? '展开菜单' : '收起菜单'"
        :placement="tooltipPlacement"
        :show-after="400"
      >
        <button
          type="button"
          class="aside-action-btn"
          :aria-label="collapse ? '展开菜单' : '收起菜单'"
          @click="emit('toggle-collapse')"
        >
          <el-icon :size="16">
            <Expand v-if="collapse" />
            <Fold v-else />
          </el-icon>
        </button>
      </el-tooltip>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowUp,
  Expand,
  Fold,
  HomeFilled,
  Lock,
  SwitchButton,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import LayoutSettings from '@/components/layout/LayoutSettings.vue'
import NotificationBell from '@/components/layout/NotificationBell.vue'

const props = withDefaults(
  defineProps<{
    userName: string
    collapse?: boolean
  }>(),
  {
    collapse: false,
  },
)

const emit = defineEmits<{
  command: [value: string]
  'toggle-collapse': []
}>()

const router = useRouter()

const popoverPlacement = computed(() => (props.collapse ? 'right-start' : 'top-start'))
const tooltipPlacement = computed(() => (props.collapse ? 'right' : 'top'))

const goPortal = () => {
  router.push('/portal')
}
</script>

<style scoped lang="scss">
.aside-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px 4px;

  &__user {
    display: flex;
    width: 100%;
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 2px;
  }

  &--collapse {
    align-items: center;
    padding: 8px 6px 4px;

    .aside-actions__user {
      width: auto;
    }

    .aside-actions__row {
      flex-direction: column;
      gap: 4px;
    }
  }
}

.aside-user {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 5px 8px 5px 5px;
  border: 1px solid transparent;
  border-radius: var(--app-radius-md, 8px);
  cursor: pointer;
  transition:
    background 0.2s ease,
    border-color 0.2s ease;

  &:hover {
    background: color-mix(in srgb, var(--app-primary, #0969da) 12%, transparent);
    border-color: color-mix(in srgb, var(--app-primary, #0969da) 16%, transparent);
  }

  &__avatar {
    flex-shrink: 0;
    --el-avatar-bg-color: #e8eef4;
    background: #e8eef4;
    color: #5b6b7c;
  }

  &__name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    font-weight: 500;
    color: var(--app-sidebar-text, #bfcbd9);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__caret {
    flex-shrink: 0;
    font-size: 12px;
    color: color-mix(in srgb, var(--app-sidebar-text, #bfcbd9) 62%, transparent);
  }
}

.aside-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: color-mix(in srgb, var(--app-sidebar-text, #bfcbd9) 88%, transparent);
  cursor: pointer;
  transition:
    color 0.2s ease,
    background 0.2s ease;

  &:hover {
    color: var(--app-sidebar-active, var(--app-primary, #0969da));
    background: color-mix(in srgb, var(--app-primary, #0969da) 14%, transparent);
  }
}

:deep(.notification-trigger),
:deep(.layout-settings-trigger) {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: color-mix(in srgb, var(--app-sidebar-text, #bfcbd9) 88%, transparent);

  &:hover {
    color: var(--app-sidebar-active, var(--app-primary, #0969da));
    background: color-mix(in srgb, var(--app-primary, #0969da) 14%, transparent);
  }
}

:deep(.el-badge) {
  display: inline-flex;
}
</style>
