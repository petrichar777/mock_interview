import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{vue,ts,tsx}'],
  safelist: [
    'from-orange-400', 'to-amber-400',
    'from-rose-400', 'to-pink-400',
    'from-emerald-400', 'to-teal-400',
    'from-violet-400', 'to-indigo-400',
    'from-cyan-400', 'to-sky-400',
    'from-orange-50/50', 'from-rose-50/50', 'from-emerald-50/50', 'from-violet-50/50', 'from-cyan-50/60',
    'card-bg-overlay', 'card-bar-overlay', 'icon-hover-target',
  ],
  theme: {
    extend: {},
  },
  plugins: [],
} satisfies Config
