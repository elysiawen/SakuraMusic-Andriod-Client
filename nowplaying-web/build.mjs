import { build } from 'esbuild';
import { cp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.dirname(fileURLToPath(import.meta.url));
const effectsRoot = path.join(root, 'effects');
const assetsRoot = path.resolve(root, '../app/src/main/assets/nowplaying');
const template = await readFile(path.join(root, 'shared/index.template.html'), 'utf8');

// 一个目录 = 一个效果；目录名同时是 assets 子目录名和 bundle 文件名。
const ids = (await readdir(effectsRoot, { withFileTypes: true }))
  .filter((entry) => entry.isDirectory())
  .map((entry) => entry.name)
  .sort();

for (const id of ids) {
  const dir = path.join(effectsRoot, id);
  const manifest = JSON.parse(await readFile(path.join(dir, 'effect.json'), 'utf8'));
  if (manifest.id !== id) {
    throw new Error(`[${id}] effect.json 的 id 与目录名不一致`);
  }

  const outDir = path.join(assetsRoot, id);
  await rm(outDir, { recursive: true, force: true });
  await mkdir(outDir, { recursive: true });

  await build({
    entryPoints: [path.join(dir, manifest.entry ?? 'main.ts')],
    bundle: true,
    format: 'esm',
    // import.meta.url 要保留（笔顺 JSON 靠它定位），所以不能低于 es2020。
    target: 'es2020',
    outfile: path.join(outDir, `${id}.js`),
    loader: {
      // 水珠精灵和桌面端一样打进包里，省一次资源请求。
      '.png': 'dataurl',
    },
    minify: true,
    sourcemap: false,
    legalComments: 'none',
    logLevel: 'info',
  });

  // 运行时按 import.meta.url 相对路径读取的数据，必须和 <id>.js 放在同一目录。
  for (const file of manifest.assets ?? []) {
    await cp(path.join(dir, file), path.join(outDir, path.basename(file)));
  }
  for (const folder of manifest.assetDirs ?? []) {
    await cp(path.join(dir, folder), path.join(outDir, path.basename(folder)), {
      recursive: true,
    });
  }

  // 授权文本随应用分发，别在通用构建里漏掉。
  const html = template
    .replaceAll('__TITLE__', manifest.title ?? id)
    .replaceAll('__ID__', id);
  await writeFile(path.join(outDir, 'index.html'), html, 'utf8');

  console.info(`[${id}] assets -> ${outDir}`);
}
