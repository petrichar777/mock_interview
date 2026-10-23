import type { RouteRecordRaw } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'

// 管理员路由
export const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        redirect: '/admin/users',
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/pages/admin/AdminUsersPage.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'categories',
        name: 'admin-categories',
        component: () => import('@/pages/admin/AdminCategoriesPage.vue'),
        meta: { title: '分类与模块' },
      },
      {
        path: 'questions',
        name: 'admin-questions',
        component: () => import('@/pages/admin/AdminQuestionsPage.vue'),
        meta: { title: '题目管理' },
      },
      {
        path: 'questions/import',
        name: 'admin-questions-import',
        component: () => import('@/pages/admin/AdminQuestionsImportPage.vue'),
        meta: { title: '导入题目' },
      },
      {
        path: 'questions/:id',
        name: 'admin-question-edit',
        component: () => import('@/pages/admin/AdminQuestionEditPage.vue'),
        meta: { title: '编辑题目' },
      },
      {
        path: 'questions/generate',
        name: 'admin-questions-generate',
        component: () => import('@/pages/admin/AdminQuestionGeneratePage.vue'),
        meta: { title: 'AI 智能生成' },
      },
      {
        path: 'questions/drafts',
        name: 'admin-questions-drafts',
        component: () => import('@/pages/admin/AdminDraftReviewPage.vue'),
        meta: { title: '草稿审核' },
      },
    ],
  },
]
