import type { RouteRecordRaw } from 'vue-router'
import AppLayout from '@/layouts/AppLayout.vue'

export const appRoutes: RouteRecordRaw[] = [
  {
    path: '/app',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/app/dashboard',
      },
      {
        path: 'dashboard',
        name: 'app-dashboard',
        component: () => import('@/pages/app/DashboardPage.vue'),
        meta: { title: '首页' },
      },
      {
        path: 'practice',
        name: 'app-practice',
        component: () => import('@/pages/app/PracticeListPage.vue'),
        meta: { title: '题库练习' },
      },
      {
        path: 'practice/:moduleId',
        name: 'app-practice-detail',
        component: () => import('@/pages/app/PracticeDetailPage.vue'),
        meta: { title: '题目详情' },
      },
      {
        path: 'mistakes',
        name: 'app-mistakes',
        component: () => import('@/pages/app/MistakeBookPage.vue'),
        meta: { title: '错题本' },
      },
      {
        path: 'interview/setup',
        name: 'app-interview-setup',
        component: () => import('@/pages/app/InterviewSetupPage.vue'),
        meta: { title: 'AI 模拟面试' },
      },
      {
        path: 'interview/:sessionId',
        name: 'app-interview-chat',
        component: () => import('@/pages/app/InterviewChatPage.vue'),
        meta: { title: '面试对话' },
      },
      {
        path: 'reports',
        name: 'app-reports',
        component: () => import('@/pages/app/InterviewReportPage.vue'),
        meta: { title: '面试记录' },
      },
      {
        path: 'resume-optimizer',
        name: 'app-resume-optimizer',
        component: () => import('@/pages/app/ResumeOptimizerPage.vue'),
        meta: { title: '简历优化' },
      },
    ],
  },
]
