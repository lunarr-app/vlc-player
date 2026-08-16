module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageInstance: 'new VLCPlayerPackage()',
      },
      ios: {},
      tvos: {},
    },
  },
};
