const appJson = require("./app.json");

module.exports = ({ config }) => {
    const versionName = process.env.APPLOCK_VERSION_NAME;
    const versionCode = process.env.APPLOCK_VERSION_CODE;

    if ((versionName && !versionCode) || (!versionName && versionCode)) {
        throw new Error("APPLOCK_VERSION_NAME and APPLOCK_VERSION_CODE must be provided together.");
    }

    if (versionName && !/^\d+\.\d+\.\d+$/.test(versionName)) {
        throw new Error(`Invalid APPLOCK_VERSION_NAME: ${versionName}`);
    }

    if (versionCode && (!/^\d+$/.test(versionCode) || Number(versionCode) < 1)) {
        throw new Error(`Invalid APPLOCK_VERSION_CODE: ${versionCode}`);
    }

    const source = config ?? appJson.expo;
    if (!versionName) return source;

    return {
        ...source,
        version: versionName,
        android: {
            ...source.android,
            versionCode: Number(versionCode),
        },
    };
};
