var app = angular.module('invoiceDetail', ['pay.app']);


app.controller('invoiceDetailCtrl', function ($scope, $http) {
    console.log("invoiceId", $scope.invoiceId);

    $scope.getInvoice = function () {
        $http.get('/api/v1/invoice/'+$scope.invoiceId, {params: {"f":"json"}})
            .success(function(data, status, headers, config) {
                console.log("data", data);
                $scope.invoice = data;

            });
    };

    $scope.getInvoice();
});
