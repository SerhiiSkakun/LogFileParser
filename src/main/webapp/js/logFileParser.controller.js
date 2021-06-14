let app = angular.module('logParserModule', ['ngFileUpload']);

app.controller('logFileParserController', Controller);
Controller.$inject = ['$scope', 'logFileParserService'];
function Controller($scope, logFileParserService) {
    let vm = this;

    vm.filter = {
        filePath: null,
        fileName: null,
        startRow: null,
        finishRow: null,
        isUniqRecords: true,
        isGatherMessages: false,
        isErrorsOnly: true,
        isTeStackTraceOnly: true
    };

    vm.parseLogFile = parseLogFile;
    vm.selectFile = selectFile;
    vm.onChangeUniqRecords = onChangeUniqRecords;

    function selectFile (file) {
        if(file) {
            vm.filter.fileName = file.name;
        }
    }

    function onChangeUniqRecords() {
        if (!vm.filter.isUniqRecords) vm.filter.isGatherMessages = false;
    }

    function parseLogFile() {
            vm.isParsingFile = true;
            logFileParserService.parseLogFile(vm.filter)
                .then(function () {
                    vm.isParsingFile = false;
                }).catch(function (error) {
                    vm.isParsingFile = false;
                    alert("Exception: " + error);
            });
    }
}