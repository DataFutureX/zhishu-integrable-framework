<template>

  <div class="layout-nav-bar" :class="{ 'layout-nav-bar--embedded': embedded }">

    <el-icon

      v-if="showCollapse"

      class="collapse-btn"

      @click="$emit('toggle-collapse')"

    >

      <Fold v-if="!collapse" />

      <Expand v-else />

    </el-icon>



    <LayoutTabs v-if="showTabBar" embedded class="layout-nav-bar__tabs" />



    <el-breadcrumb v-else-if="showBreadcrumb" separator="/">

      <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">

        {{ item.meta?.title }}

      </el-breadcrumb-item>

    </el-breadcrumb>

  </div>

</template>



<script setup lang="ts">

import type { RouteLocationMatched } from 'vue-router'

import { Fold, Expand } from '@element-plus/icons-vue'

import LayoutTabs from '@/components/layout/LayoutTabs.vue'



defineProps<{

  collapse: boolean

  showCollapse: boolean

  showTabBar: boolean

  showBreadcrumb: boolean

  breadcrumbs: RouteLocationMatched[]

  embedded?: boolean

}>()



defineEmits<{

  'toggle-collapse': []

}>()

</script>



<style lang="scss" scoped>

.layout-nav-bar {

  display: flex;

  align-items: center;

  gap: 12px;

  flex: 1;

  min-width: 0;

  width: 100%;



  &--embedded {

    .collapse-btn {

      color: var(--app-header-text);



      &:hover {

        color: #fff;

        background-color: rgba(255, 255, 255, 0.12);

      }

    }



    :deep(.el-breadcrumb__item) {

      .el-breadcrumb__inner {

        color: rgba(255, 255, 255, 0.72);



        &:hover {

          color: #fff;

        }

      }



      &:last-child .el-breadcrumb__inner {

        color: #fff;

        font-weight: 600;

      }

    }



    :deep(.layout-tabs__item) {
      color: rgba(255, 255, 255, 0.78);
      border-color: rgba(255, 255, 255, 0.18);
      background: rgba(255, 255, 255, 0.08);

      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.14);
      }
    }

    :deep(.layout-tabs__item--active) {
      color: var(--app-primary-dark);
      background: #fff;
      border-color: #fff;
    }



    :deep(.layout-tabs__more) {

      color: var(--app-header-text);

      border-color: rgba(255, 255, 255, 0.25);

      background: rgba(255, 255, 255, 0.08);

    }

  }



  .collapse-btn {

    display: flex;

    align-items: center;

    justify-content: center;

    width: 32px;

    height: 32px;

    font-size: 18px;

    color: var(--app-text-regular);

    cursor: pointer;

    border-radius: var(--app-radius-md);

    transition: all 0.22s ease;

    flex-shrink: 0;



    &:hover {

      color: var(--app-primary);

      background-color: rgba(9, 105, 218, 0.1);

    }

  }



  &__tabs {

    flex: 1;

    min-width: 0;

  }



  :deep(.el-breadcrumb) {

    min-width: 0;



    .el-breadcrumb__item {

      .el-breadcrumb__inner {

        font-weight: 500;

        color: var(--app-text-regular);

        transition: color 0.22s ease;



        &:hover {

          color: var(--app-primary);

        }

      }



      &:last-child {

        .el-breadcrumb__inner {

          color: var(--app-text-primary);

          font-weight: 600;

        }

      }

    }

  }

}

</style>


