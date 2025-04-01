import puppeteer from 'puppeteer';
import fs from 'fs';
import path from 'path';

const downloadPath = path.resolve('.'); // Set download directory

// Ensure the directory exists
if (!fs.existsSync(downloadPath)) {
    fs.mkdirSync(downloadPath, { recursive: true });
}

// Function to initialize Puppeteer and set download behavior
async function setupBrowser() {
    const browser = await puppeteer.launch({ headless: true, args:['--no-sandbox'] });
    const page = await browser.newPage();

    const client = await page.target().createCDPSession();
    await client.send('Page.setDownloadBehavior', {
        behavior: 'allow',
        downloadPath: downloadPath,
    });

    return { browser, page };
}

async function login(page) {
    console.log('Navigating to LOINC login page...');
    await page.goto('https://loinc.org/wp-login.php?redirect_to=https%3A%2F%2Floinc.org%2Fdownloads%2F', { waitUntil: 'load' });

    console.log('Filling in credentials...');
    await page.type('#user_login', process.env.LOINC_USERNAME);
    await page.type('#user_pass', process.env.LOINC_PASSWORD);

    console.log('Submitting login form...');
    await Promise.all([
        page.click('#wp-submit'),
        page.waitForNavigation({ waitUntil: 'networkidle2' }),
    ]);

    console.log('Login successful!');
}

async function downloadLatestReleaseFile(page) {
    console.log('Starting download of the latest release...');

    await page.click('.fa-download');
    await page.waitForNavigation({ waitUntil: 'networkidle2' });

    console.log('Accepting terms and conditions...');
    await page.click('#tc_accepted_');
    await page.click('.dlm-tc-submit');

    console.log('Waiting for download to complete...');
    const fileDownloaded = await waitForDownload(downloadPath, 30000);

    if (fileDownloaded) {
        console.log('LOINC download complete!');
    } else {
        console.log('LOINC Download timeout reached.');
    }
}

async function downloadSpecificReleaseFile(page, version) {
    console.log('Navigating to LOINC archive page...');
    await page.goto('https://loinc.org/downloads/archive/', { waitUntil: 'networkidle2' });

    console.log('Searching for version ' + version + '...');

    const downloadSelector = `a[href*='loinc-${version.replace(/\./g, "-")}']`; // e.g. match <a href="https://loinc.org/download/loinc-2-78-complete/?tmstv=1743515220">
    const downloadLink = await page.evaluate((selector) => {
        const link = document.querySelector(selector);
        return link ? link.href : null;
    }, downloadSelector);

    if (!downloadLink) {
        console.log(`Version ${version} not found.`);
        return;
    }

    console.log(`Found download link: ${downloadLink}`);
    await page.goto(downloadLink, { waitUntil: 'networkidle2' });

    console.log('Accepting terms and conditions...');
    await page.click('#tc_accepted_');
    await page.click('.dlm-tc-submit');

    console.log('Waiting for download to complete...');
    const fileDownloaded = await waitForDownload(downloadPath, 30000);

    if (fileDownloaded) {
        console.log(`LOINC version ${version} downloaded successfully!`);
    } else {
        console.log(`Timeout reached. LOINC version ${version} download failed.`);
    }
}


async function waitForDownload(dir, timeout = 30000) {
    const start = Date.now();

    while (Date.now() - start < timeout) {
        const files = await fs.promises.readdir(dir);
        if (files.some(file => file.startsWith('Loinc') && file.endsWith('.zip'))) {
            return true; // Download complete
        }
        await new Promise(resolve => setTimeout(resolve, 1000)); // Wait 1 sec before checking again
    }

    return false; // Timeout reached
}

async function main() {
    try {
        const version = process.argv[2];
        const { browser, page } = await setupBrowser();
        await login(page);
        if(version) {
            await downloadSpecificReleaseFile(page, version);
        } else {
            await downloadLatestReleaseFile(page);
        }
        await browser.close();
        console.log('Browser closed.');
    } catch (error) {
        console.error('An error occurred:', error);
    }
}

main();
