var app = angular.module('store', ['ngResource']);

app.controller('StoreCtrl', function($scope, $http, $window) {
  $scope.doPay = function() {
    console.log('do pay');
    var param = {
      currency: 'cny',
      price: 3000
    };

    $http.post('https://pay.coinport.com/invoice', param)
      .success(function(data, status, headers, config) {
        console.log(data);
      }).error(function(data, status, headers, config) {
        console.log(status, data);
      });
  };

});
