import { onBeforeUnmount, onMounted, ref } from 'vue';

/**
 * 读主题色 `--accent` / `--accent-2`（原始 CSS 字符串），并跟着 documentElement 上的
 * `data-theme` / `data-palette` 变化重新读。
 *
 * 沉浸模式要把它们转成 HSL 再混进流体色块；雾笺/绘光直接当 folia Theme 的
 * accentColor / secondaryColor 用。取不到时由各自兜底。
 */
export function useThemeAccent() {
  const accentCss = ref('');
  const accent2Css = ref('');

  function readThemeColors(): void {
    const style = getComputedStyle(document.documentElement);
    accentCss.value = style.getPropertyValue('--accent').trim();
    accent2Css.value = style.getPropertyValue('--accent-2').trim();
  }

  let themeObserver: MutationObserver | null = null;

  onMounted(() => {
    readThemeColors();
    // 主题切换只改 documentElement 上的 data 属性（配色 palette / 明暗 theme）
    themeObserver = new MutationObserver(readThemeColors);
    themeObserver.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['data-theme', 'data-palette'],
    });
  });

  onBeforeUnmount(() => themeObserver?.disconnect());

  return { accentCss, accent2Css, readThemeColors };
}
