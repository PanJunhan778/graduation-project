<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  subtitle?: string
  actionCount?: number
  filterCount?: number
  rowCount?: number
  showSearch?: boolean
  showPagination?: boolean
}>(), {
  subtitle: '',
  actionCount: 2,
  filterCount: 0,
  rowCount: 8,
  showSearch: true,
  showPagination: true,
})
</script>

<template>
  <div class="page-table-skeleton">
    <div class="skeleton-card">
      <div class="page-header">
        <div>
          <div class="sk-block sk-title" />
          <div v-if="subtitle" class="sk-block sk-subtitle" />
        </div>
      </div>

      <div class="action-bar">
        <div class="action-left">
          <div
            v-for="action in actionCount"
            :key="`action-${action}`"
            class="sk-block sk-action"
          />
        </div>
        <div v-if="showSearch" class="action-right">
          <div class="sk-block sk-search" />
        </div>
      </div>

      <div v-if="filterCount > 0" class="filter-bar">
        <div
          v-for="filter in filterCount"
          :key="`filter-${filter}`"
          class="sk-block sk-filter"
        />
      </div>

      <div class="table-shell">
        <div class="table-head">
          <div
            v-for="column in 6"
            :key="`head-${column}`"
            class="sk-block sk-head"
          />
        </div>

        <div class="table-body">
          <div
            v-for="row in rowCount"
            :key="`row-${row}`"
            class="table-row"
          >
            <div
              v-for="column in 6"
              :key="`cell-${row}-${column}`"
              class="sk-block"
              :class="column === 6 ? 'sk-cell-short' : 'sk-cell'"
            />
          </div>
        </div>
      </div>

      <div v-if="showPagination" class="pagination-shell">
        <div class="sk-block sk-pagination" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-table-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.skeleton-card {
  background: #ffffff;
  border: 1px solid rgba(0, 0, 0, 0.08);
  border-radius: 14px;
  box-shadow:
    rgba(0, 0, 0, 0.03) 0px 4px 16px,
    rgba(0, 0, 0, 0.02) 0px 1px 4px;
  overflow: hidden;
  position: relative;
}

.skeleton-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, #0075de 0%, #3d92ff 40%, #213183 100%);
  z-index: 1;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
  padding: 18px 20px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 14px;
  flex-wrap: wrap;
  padding: 14px 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
  background: rgba(246, 245, 244, 0.4);
}

.table-shell {
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  padding: 18px 20px;
}

.table-head,
.table-row {
  display: grid;
  grid-template-columns: 1.2fr 0.9fr 0.9fr 0.9fr 1fr 0.8fr;
  gap: 16px;
  align-items: center;
}

.table-head {
  padding-bottom: 14px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.table-body {
  display: flex;
  flex-direction: column;
}

.table-row {
  padding: 18px 0;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.table-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.pagination-shell {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.sk-block {
  position: relative;
  overflow: hidden;
  border-radius: 999px;
  background: #efece9;
}

.sk-block::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.95), transparent);
  animation: shimmer 1.4s ease infinite;
}

.sk-title {
  width: 160px;
  height: 28px;
}

.sk-subtitle {
  width: 320px;
  height: 14px;
  margin-top: 10px;
}

.sk-action {
  width: 128px;
  height: 36px;
}

.sk-search {
  width: 220px;
  height: 36px;
}

.sk-filter {
  width: 148px;
  height: 36px;
}

.sk-head {
  height: 13px;
}

.sk-cell {
  height: 14px;
}

.sk-cell-short {
  height: 14px;
  width: 72%;
}

.sk-pagination {
  width: 240px;
  height: 34px;
}

@keyframes shimmer {
  100% {
    transform: translateX(100%);
  }
}

@media (max-width: 960px) {
  .table-head,
  .table-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .sk-search {
    width: 180px;
  }
}

@media (max-width: 640px) {
  .action-bar,
  .filter-bar,
  .table-shell {
    padding: 16px;
  }

  .table-head,
  .table-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .sk-subtitle,
  .sk-search {
    width: 100%;
  }

  .pagination-shell {
    justify-content: stretch;
  }

  .sk-pagination {
    width: 100%;
  }
}
</style>
