import { ref, watch, type Ref } from 'vue';
import { extractPalette, type CoverPalette } from '@/utils/palette';

/**
 * 封面取色（沉浸模式的流体色块、雾笺/绘光的 primaryColor 都用它）。
 * 等待期间又切歌了就让旧结果作废，别用旧封面颜色染新封面。
 */
export function useCoverPalette(cover: Readonly<Ref<string | undefined>>) {
  const palette = ref<CoverPalette | null>(null);

  watch(
    cover,
    (url) => {
      if (!url) {
        palette.value = null;
        return;
      }
      void extractPalette(url).then((result) => {
        if (cover.value === url) palette.value = result;
      });
    },
    { immediate: true },
  );

  return palette;
}
