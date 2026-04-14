const { test, expect } = require('playwright/test');
const path = require('path');

test.describe.configure({ mode: 'serial' });

test('capture hr dashboard views', async ({ browser, request }) => {
  const baseUrl = process.env.QA_BASE_URL;
  const username = process.env.QA_USERNAME;
  const password = process.env.QA_PASSWORD;
  const outDir = process.env.QA_OUT_DIR;

  expect(baseUrl).toBeTruthy();
  expect(username).toBeTruthy();
  expect(password).toBeTruthy();
  expect(outDir).toBeTruthy();

  const loginRes = await request.post(`${baseUrl}/api/auth/login`, {
    data: { username, password },
  });
  expect(loginRes.ok()).toBeTruthy();

  const payload = await loginRes.json();
  expect(payload.code).toBe(200);

  const auth = payload.data;
  const userInfo = {
    role: auth.role,
    realName: auth.realName,
    companyName: auth.companyName || '',
    companyCode: auth.companyCode || '',
    industry: auth.industry || '',
    taxpayerType: auth.taxpayerType || '',
  };

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
});
