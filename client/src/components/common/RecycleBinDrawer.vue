<script setup lang="ts">
type RecycleBinRow = Record<string, any>

const props = defineProps<{
  modelValue: boolean
  title: string
  data: RecycleBinRow[]
  loading: boolean
  total: number
  page: number
  size: number
  selectedCount: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'selection-change': [rows: any[]]
  'page-change': [page: number]
  'size-change': [size: number]
  restore: [row: any]
  'batch-restore': []
}>()

function closeDrawer() {
  emit('update:modelValue', false)
}

function handleSelectionChange(rows: RecycleBinRow[]) {
  emit('selection-change', rows)
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    direction="rtl"
    size="920px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="recycle-bin-toolbar">
      <div class="toolbar-copy">
        <p class="toolbar-title">仅展示当前模块已逻辑删除的数据</p>
        <p class="toolbar-hint">支持单条恢复和批量恢复，恢复后会重新回到主列表。</p>
      </div>
      <el-button type="primary" plain :disabled="selectedCount === 0" @click="emit('batch-restore')">
        批量恢复{{ selectedCount ? ` (${selectedCount})` : '' }}
      </el-button>
    </div>

    <el-table
      :data="data"
      v-loading="loading"
      style="width: 100%"
      :header-cell-style="{
        background: '#f6f5f4',
        color: '#615d59',
        fontWeight: 600,
        fontSize: '13px',
        height: '44px',
      }"
      :row-style="{ height: '48px' }"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="48" align="center" />
      <slot name="columns" />
      <el-table-column prop="deletedTime" label="删除时间" width="180" />
      <el-table-column label="删除人" width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.deletedByName || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="emit('restore', row)">
            恢复
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="emit('page-change', $event)"
        @size-change="emit('size-change', $event)"
      />
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="closeDrawer">关闭</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<style scoped>
.recycle-bin-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.toolbar-title {
  font-size: 14px;
  font-weight: 600;
  color: #383430;
}

.toolbar-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #7a746d;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding: 8px 0;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
