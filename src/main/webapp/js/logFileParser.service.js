app.factory('logFileParserService', Service);
Service.$inject = ['$http', '$q'];
function Service($http, $q) {
    return {
        parseLogFile: parseLogFile
    };

    function parseLogFile(filter) {
        let deferred = $q.defer();
        $.fileDownload('./parseLogFile?actionName=parseLogFile',
            {
                httpMethod: 'POST',
                data: {data: JSON.stringify(filter)},
                successCallback: function (response) {
                    deferred.resolve(response.data);
                },
                failCallback: function (error) {
                    let errorMessage = JSON.parse((new DOMParser().parseFromString(error,"text/xml").getElementsByTagName("pre"))[0].childNodes[0].nodeValue).error;
                    deferred.reject(errorMessage);
                }
            });
        return deferred.promise;
    }
}