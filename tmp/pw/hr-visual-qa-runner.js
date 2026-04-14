const { chromium } = require('playwright');
const path = require('path');

async function main() {
  const baseUrl = process.env.QA_BASE_URL;
  const username = process.env.QA_USERNAME;
  const password = process.env.QA_PASSWORD;
  const outDir = process.env.QA_OUT_DIR;

  if (!baseUrl || !username || !password || !outDir) {
    throw new Error('Missing QA environment variables.');
  }

  const loginRes = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const payload = await loginRes.json();

  if (payload.code !== 200) {
    throw new Error(`Login failed: ${JSON.stringify(payload)}`);
  }

  const auth = payload.data;
  const userInfo = {
    role: auth.role,
    realName: auth.realName,
    companyName: auth.companyName || '',
    companyCode: auth.companyCode || '',
    industry: auth.industry || '',
    taxpayerType: auth.taxpayerType || '',
  };

  const browser = await chromium.launch({ headless: true });

  async function capture(name, contextOptions, fullPage = false) {
    const context = await browser.newContext(contextOptions);
    await context.addInitScript(({ token, info }) => {
      localStorage.setItem('ems_token', token);
      localStorage.setItem('ems_user', JSON.stringify(info));
    }, { token: auth.token, info: userInfo });

    const page = await context.newPage();
    await page.goto(`${baseUrl}/dashboard`, { waitUntil: 'networkidle' });
    await page.locator('.el-tabs__item').filter({ hasText: '人事洞察' }).click();
    await page.getByText('当前团队结构画像').waitFor({ state: 'visible', timeout: 10000 });
    await page.waitForTimeout(1600);
    await page.screenshot({
      path: path.join(outDir, name),
      fullPage,
    });
    await context.close();
  }

  await capture('hr-desktop-fold.png', { viewport: { width: 1440, height: 900 } }, false);
  await capture('hr-desktop-full.png', { viewport: { width: 1440, height: 900 } }, true);
  await capture('hr-tablet-fold.png', { viewport: { width: 1024, height: 900 } }, false);
  await capture(
    'hr-mobile-full.png',
    {
      viewport: { width: 390, height: 844 },
      isMobile: true,
      hasTouch: true,
      deviceScaleFactor: 2,
    },
    true,
  );

  await browser.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
